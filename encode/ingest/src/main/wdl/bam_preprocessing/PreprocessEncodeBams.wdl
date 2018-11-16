version 1.0

workflow PreprocessEncodeBam {

  input {
    String bam_accession
    File bam_path
  }

  call SortAndIndexBam {
    input:
      bam_path = bam_path
  }

  output {
    String accession = bam_accession
    File bam_path = SortAndIndexBam.sorted_bam
    File bai_path = SortAndIndexBam.sorted_bam_index
    Float bam_size_mb = size(SortAndIndexBam.sorted_bam, "MB")
    String bam_md5 = read_string(SortAndIndexBam.sorted_bam_md5)
  }
}

task SortAndIndexBam {
  input {
    File bam_path
  }

  String bam_base = basename(bam_path, ".bam")
  String sorted_base = bam_base + ".sorted"
  String sorted_bam_name = sorted_base + ".bam"

  # These two are inferred by convention from Picard,
  # but included here so everything is in one place.
  String sorted_index_name = sorted_base + ".bai"
  String sorted_md5_name = sorted_bam_name + ".md5"

  # Magic constants copied from the production germline single-sample pipeline...
  # The explanation from that workflow is:
  # We only store 300000 records in RAM because it's faster for our data, so SortSam ends up spilling a lot to disk.
  # The spillage is also in an uncompressed format, so we need to account for that with a larger multiplier.
  Float disk_multiplier = 3.25
  Int disk_wiggle_room = 20
  Int disk_size = (ceil(disk_multiplier * size(bam_path, "GB")) + disk_wiggle_room)

  # Also copied from what the production workflow does; give 5GB of memory to the VM.
  Int vm_mem_mb = 5120

  command <<<
    set -ex -o pipefail

    java -Dsamjdk.compression_level=5 -Xmx~{vm_mem_mb - 1024}m \
      -jar /app/picard.jar \
      SortSam \
      INPUT=~{bam_path} \
      OUTPUT=~{sorted_bam_name} \
      SORT_ORDER=coordinate \
      CREATE_INDEX=true \
      CREATE_MD5_FILE=true \
      MAX_RECORDS_IN_RAM=300000 \
      VALIDATION_STRINGENCY=LENIENT
  >>>

  runtime {
    docker: "us.gcr.io/broad-gdr-encode/picard-alpine:2.18.14"
    disks: "local-disk " + disk_size + " HDD"
    cpu: 1
    memory: vm_mem_mb + " MB"
  }

  output {
    File sorted_bam = sorted_bam_name
    File sorted_bam_index = sorted_index_name
    File sorted_bam_md5 = sorted_md5_name
  }
}
