CREATE TABLE donors (
    donor_id text PRIMARY KEY,
    age integer NULL,
    age_units text NULL,
    health_status text NULL,
    sex text NULL,
    more_info text NULL
);

CREATE TABLE files (
    file_id text PRIMARY KEY,
    data_source text NOT NULL,
    assay_type text NOT NULL,
    reference_genome_assembly text NULL,
    data_quality_category text NOT NULL,
    biosample_term_id text NOT NULL,
    biosample_type text NOT NULL,
    biosamples text[] NOT NULL,
    cell_type text NOT NULL,
    date_file_created timestamp NOT NULL,
    derived_from_exp text[] NOT NULL,
    derived_from_ref text[] NOT NULL,
    participant_ids text[] NOT NULL,
    experiments text[] NOT NULL,
    file_format text NOT NULL,
    file_size_MB double precision NOT NULL,
    file_format_subtype text NOT NULL,
    file_available_in_gcs boolean NOT NULL,
    labs_generating_data text[] NOT NULL,
    DNA_library_ids text[] NOT NULL,
    md5sum text NOT NULL,
    data_type text NOT NULL,
    paired_end_sequencing boolean NULL,
    read_count bigint NULL,
    replicate_ids uuid[] NOT NULL,
    target_of_assay text NULL,
    more_info text NOT NULL,
    data_review_summary text[] NOT NULL,
    file_gs_path text NULL,
    read_length bigint NULL,
    percent_aligned_reads double precision NULL,
    percent_duplicated_reads double precision NULL
);
