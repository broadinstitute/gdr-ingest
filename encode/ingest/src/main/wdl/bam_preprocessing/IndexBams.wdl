workflow IndexBams {

  # array of gsutil paths
  Array[File] bam_input_paths

  scatter (bam_input_path in bam_input_paths) {
    call IndexBam {
    input:
      bam_input_path = bam_input_path
    }
  }

  output {
    Array[String] bam_index_output_paths = IndexBam.bam_index_output_path
  }
}

task IndexBam {
  # gsutil path and google bucket to output paths
  String bam_input_path
  String bam_index_output_path_name = bam_input_path + ".bai"

  # output name for index bams
  String bam_index_output_file_name = basename(bam_input_path) + ".bai"

  command <<<
    # output bam index (stream with gsutil paths)
    samtools index -b ${bam_input_path} ${bam_index_output_file_name}

    # copy to bucket
    gsutil cp ${bam_index_output_file_name} ${bam_index_output_path_name}
  >>>

  runtime {
    docker: "us.gcr.io/broad-gdr-encode/samtools-with-gsutil:1.0"
    disks: "local-disk 1 HDD"
    cpu: 1
    memory: "3.75 GB"
  }

  output {
    String bam_index_output_path = bam_index_output_path_name
  }
}


