from airflow import DAG
from airflow.operators.bash_operator import BashOperator
from airflow.operators.python_operator import PythonOperator
from airflow.operators import SimpleHttpOperator, HttpSensor, BaseSensorOperator
from airflow import AirflowException
from datetime import datetime, date
import os
import sys
import json
from pprint import pprint

from cromwell_tools import cromwell_tools
import googleapiclient.discovery
from googleapiclient import discovery
import encode_transfer_gcp

encode_jar = "/Users/aurora/Desktop/repositories/gdr-ingest/encode-ingest/target/scala-2.12/encode-ingest-assembly-0.1.0-SNAPSHOT.jar" # change me
local_path = "/Users/aurora/Desktop/repositories/gdr-ingest/encode-ingest/src/main/airflow"  # change me
wdl_file = '/Users/aurora/cromwell/myWorkflow.wdl' # change me
caas_key='/Users/aurora/.secrets.json' # change me
cromwell_url="http://localhost:8080/api/workflows/v1"
read_inputs_file="{}"
urllist = 'https://storage.googleapis.com/broad-gdr-encode-ingest-staging/10-15-2018/sts-manifest.tsv'

gcp_bucket = 'broad-gdr-encode-test'
gcp_project = 'broad-gdr-encode-storage'


storagetransfer = googleapiclient.discovery.build('storagetransfer', 'v1')
read_wdl_file = open(wdl_file, 'rb')
directory_date = date.today() # will look like: yyyy-mm-dd

default_args = {
  'owner': 'airflow', # the owner of the task, using the unix username is recommended
  'provide_context': True
}

dag = DAG(
  dag_id='encode',
  default_args = default_args,
  start_date=datetime.now(),
  schedule_interval='@once'
)

# Pull down files from ENCODE and store them locally in a directory TODO-- this will transfer directly to gcp
taskEncodeDownload = BashOperator(
  task_id = 'encode_fetch',
  dag = dag,
  params={'encode_jar': encode_jar, 'local_path': local_path},
  bash_command = 'java -jar {{ params.encode_jar }} prep-ingest --output-dir {{ params.local_path }}/encode_files/'
  )

# Push now local files from sts_files directory into the broad-gdr-encode-ingest-staging bucket as a new directory w name based on date
taskEncodeUpload = BashOperator(
    task_id = 'encode_upload',
    dag = dag,
    params={'local_path': local_path, 'directory_date': directory_date},
    bash_command = "gsutil -m cp {{ params.local_path }}/encode_files/*  gs://broad-gdr-encode-ingest-staging/{{ params.directory_date }}"
    # TODO should this overwrite whatever is already in the remote directory?
   )

# Order task download before re-upload
taskEncodeDownload >> taskEncodeUpload


# Once the files are in gcp -- start a one - off transfer job to run immediately and copy the files from the urllist to GCS

def transfer_to_gcp(ds, **kwargs):
    job = encode_transfer_gcp.Transfer(gcp_project).from_urllist(urllist, gcp_bucket, description="ENCODE refresh on {}".format(directory_date))
    job_name = job["name"]
    return job_name

taskDataTransfer = PythonOperator(
    task_id = 'data_transfer',
    params={'directory_date': directory_date},
    dag = dag,
    python_callable = transfer_to_gcp)

# create sensor to check on status of transfer job
class TransferSensor(BaseSensorOperator):
    def poke(self, context):
        job_name = context['ti'].xcom_pull('data_transfer')
        status = encode_transfer_gcp.Transfer(gcp_project).get_transfer_status(job_name)
        pprint(status)
        if status == 'SUCCESS':
            return True
        if status == 'FAILED' or 'ABORTED':
            raise ValueError('GCS Transfer job has failed or was canceled') #---should it try the transfer again?
        return False

sensorTransferStatus = TransferSensor(
  task_id = 'transfer_status',
  params={},
  mode = 'reschedule',  # If reschedule, use first start date of current try
  dag = dag)
  # add failure option --failed or aborted?

# Order task transfer before polling
taskDataTransfer >> sensorTransferStatus


# Now kick off a cromwell job and poller to id when it's done

def cromwell_job(**kwargs):
    response = cromwell_tools.start_workflow(wdl_file=read_wdl_file, inputs_file=read_inputs_file, url=cromwell_url, caas_key=caas_key) # TODO JINJA this?
    pprint(response.content)
    response_json = json.loads(response.content)
    pprint(response_json['id']) # should this check to see if status was submitted
    job_id = response_json['id']
    return job_id

taskCromwellJob = PythonOperator(
  task_id = 'cromwell_kicker',
  dag = dag,
  python_callable = cromwell_job)

  # TODO WARNING NOTE must set export AIRFLOW_CONN_CROMWELL_URL="http://localhost:8080" before the sensor will work!

sensorCromwellStatus = HttpSensor(
  task_id = 'cromwell_status',
  conn_id = 'CROMWELL_URL',
  http_conn_id = 'CROMWELL_URL',
  endpoint="api/workflows/v1/{{ ti.xcom_pull('cromwell_kicker') }}/status", #if taskCromwellJob has not been re-run, will use previous job id
  params={},
  response_check=lambda response: True if "Succeeded" in response.content else False,
  mode = 'reschedule',  # If reschedule, use first start date of current try
  dag=dag)
  # add failure option --failed or aborted?

# Order task transfer before polling
taskCromwellJob >> sensorCromwellStatus
