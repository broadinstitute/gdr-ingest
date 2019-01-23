version 1.0

workflow CheckSorted {

  input {
    File bam_fofn
  }

  call CheckBamsSorted {
    input:
      bam_fofn = bam_fofn
  }
}

task SplitBatches {
  input {
    File bam_fofn
  }

  String prefix = "foo-"

  command <<<
    split balkjfsadl;f
  >>>

  output {
    Array[File] fofns = glob(prefix + "*")
  }
}

task CheckBamsSorted {
  input {
    File bam_fofn
  }

  String report_name = "sorted-report.tsv"

  command <<<
    set -euo pipefail

    # Define the logic that we want to run on each bam in a function because it's easier than
    # trying to get the quotes right when inlining the commands into the parallel call.
    function is_sorted () {
      samtools view -H "$1" | grep -Eo "(SO:\w+)" | awk 'BEGIN {FS=":"}; {print ($2=="coordinate") ? "true" : "false"}'
    }

    while read bam; do
      echo "$bam,$(is_sorted ${bam})" >> ~{report_name}
    done<
  >>>

  runtime {
    docker: "us.gcr.io/broad-gdr-encode/samtools-with-parallel:1.0"
    disks: "local-disk 1 HDD"
    cpu: 1
    memory: "3.75 GB"
  }

  output {
    File report = report_name
  }
}
