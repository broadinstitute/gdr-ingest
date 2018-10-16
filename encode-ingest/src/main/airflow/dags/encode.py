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

gcp_bucket = 'broad-gdr-encode-test'
gcp_project = 'broad-gdr-encode-storage'
gcp_ingest = 'broad-gdr-encode-ingest-staging'
s3_bucket = 'dig-analysis-data'
s3_paths = []
gcp_project_gdr = 'broad-gdr-dig'


storagetransfer = googleapiclient.discovery.build('storagetransfer', 'v1')
read_wdl_file = open(wdl_file, 'rb')
directory_date = date.today() # will look like: yyyy-mm-dd
urllist = 'https://storage.googleapis.com/{}/{}/sts-manifest.tsv'.format(gcp_ingest, directory_date)

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



# Submits a request to the Storage Transfer Service to copy the contents of s3://dig-analysis-data into gs://broad-gdr-dig-storage
# Uses a sensor to wait for the transfer job to finish

class TransferS3toGCPSensor(BaseSensorOperator):
    job = encode_transfer_gcp.Transfer(gcp_project).from_s3(s3_bucket, s3_paths, gcp_bucket, description="GDR refresh on {}".format(directory_date)) #overwrite_existing=False
    job_name = job["name"] # what happens when this ^ fails?
    pprint(job_name)
    pprint(s3_bucket)
    pprint(s3_paths)
    pprint(gcp_bucket)

    def poke(self, context):
        status = encode_transfer_gcp.Transfer(gcp_project).get_transfer_status(self.job_name)
        pprint(status)
        if status == 'SUCCESS':
            return True
        if status == 'FAILED' or status == 'ABORTED':
            raise ValueError('GCS Transfer job has failed or was canceled')
        return False

sensorTransferS3 = TransferS3toGCPSensor(
  task_id = 'transfer_S3',
  params={},
  mode = 'reschedule',  # If reschedule, use first start date of current try
  dag = dag)





# Pull down files from ENCODE and store them locally in a directory TODO-- this will transfer directly to gcp
taskEncodeDownload = BashOperator(
  task_id = 'encode_fetch',
  dag = dag,
  params={'encode_jar': encode_jar, 'local_path': local_path},
  bash_command = 'java -jar {{ params.encode_jar }} prep-ingest --output-dir {{ params.local_path }}/encode_files/'
  )

taskEncodeTesting = BashOperator( # for testing only
  task_id = 'encode_shorten',
  dag = dag,
  params={'encode_jar': encode_jar, 'local_path': local_path},
  bash_command = "sed -i '' '10,$ d' {{ params.local_path }}/encode_files/sts-manifest.tsv"
  )

# Push now local files from sts_files directory into the gcp_ingest bucket as a new directory w name based on date
taskEncodeUpload = BashOperator(
    task_id = 'encode_upload',
    dag = dag,
    params={'local_path': local_path, 'gcp_ingest': gcp_ingest, 'directory_date': directory_date},
    bash_command = "gsutil -m cp {{ params.local_path }}/encode_files/*  gs://{{ params.gcp_ingest }}/{{ params.directory_date }}"
    # TODO should this overwrite whatever is already in the remote directory?
   )

# Order task download before re-upload
taskEncodeDownload.set_downstream(taskEncodeUpload)

# Once the files are in gcp -- start a one-off transfer job to run immediately and copy the files from the urllist to GCS & poll until done

class TransferURLtoGCPSensor(BaseSensorOperator):
    job = encode_transfer_gcp.Transfer(gcp_project).from_urllist(urllist, gcp_bucket, description="ENCODE refresh {}".format(directory_date))
    job_name = job["name"] # what happens when this ^ fails?

    def poke(self, context):
        status = encode_transfer_gcp.Transfer(gcp_project).get_transfer_status(self.job_name)
        pprint(status)
        if status == 'SUCCESS':
            return True
        if status == 'FAILED' or status == 'ABORTED':
            raise ValueError('GCS Transfer job has failed or was canceled')
        return False

sensorTransferURL = TransferURLtoGCPSensor(
  task_id = 'transfer_URL',
  params={},
  mode = 'reschedule',  # If reschedule, use first start date of current try
  dag = dag)

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
taskCromwellJob.set_downstream(sensorCromwellStatus)
