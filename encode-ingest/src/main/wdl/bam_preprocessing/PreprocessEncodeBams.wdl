version 1.0

struct EncodeBam {
  String bam_href
  String? bai_href
  Int bam_size
  String bam_md5
}

workflow PreprocessEncodeBams {

  input {
    Array[EncodeBam] bams
  }

  scatter (bam_info in bams) {
    call SortAndIndexBam {
      input:
        bam_info = bam_info
    }

    EncodeBam processed_bam = object {
      bam_href: SortAndIndexBam.sorted_bam,
      bai_href: SortAndIndexBam.sorted_bam_index,
      bam_size: SortAndIndexBam.sorted_bam_size,
      bam_md5: SortAndIndexBam.sorted_bam_md5
    }
  }

  output {
    Array[EncodeBam] sorted_bams = processed_bam
  }
}

task SortAndIndexBam {
  input {
    EncodeBam bam_info
  }

  String bam_base = basename(bam_info.bam_href, ".bam")
  String raw_bam_name = bam_base + ".raw.bam"
  String sorted_base = bam_base + ".sorted"
  String sorted_bam_name = sorted_base + ".bam"

  Float bam_size_gb = bam_info.bam_size / 1073741824.0
  # Copied from the production germline single-sample pipeline...
  # We only store 300000 records in RAM because it's faster for our data, so SortSam ends up spilling a lot to disk.
  # The spillage is also in an uncompressed format, so we need to account for that with a larger multiplier.
  Float disk_multiplier = 3.25
  Int disk_size = (ceil(disk_multiplier * bam_size_gb) + 20)

  Int vm_mem_mb = 5120

  command <<<
    set -ex -o pipefail

    # Pull the raw bam from ENCODE. We do this here, instead of as a pre-processing step for the workflow,
    # so the raw data doesn't end up lying around GCS forever burning cash.
    #
    # NOTE this setup 100% breaks call-caching based on file contents. We rely on the caller to provide an
    # accurate value for the md5 field in the "bam info" struct to cache reliably.

    wget --output-document ~{raw_bam_name} \
      --tries=50 \
      --waitretry=60 \
      ~{bam_info.bam_href}

    java -Dsamjdk.compression_level=2 -Xmx~{vm_mem_mb - 1024} \
      -jar /app/picard.jar \
      SortSam \
      INPUT=~{raw_bam_name} \
      OUTPUT=~{sorted_bam_name} \
      SORT_ORDER=coordinate \
      CREATE_INDEX=true \
      CREATE_MD5_FILE=true \
      MAX_RECORDS_IN_RAM=300000
  >>>

  runtime {
    docker: "us.gcr.io/broad-gdr-encode/picard-alpine:1-SNAPSHOT"
    disks: "local-disk " + disk_size + " HDD"
    cpu: 1
    memory: vm_mem_mb + " MB"
  }

  output {
    # NOTE: Output as a File, instead of wrapped up in a struct, to force delocalization.
    File sorted_bam = sorted_bam_name
    File sorted_bam_index = sorted_base + ".bai"
    Int sorted_bam_size = size(sorted_bam_name)
    String sorted_bam_md5 = read_string(sorted_bam_name + ".md5")
  }
}
