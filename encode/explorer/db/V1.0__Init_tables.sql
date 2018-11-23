DROP TABLE IF EXISTS donors;
CREATE TABLE donors (
    donor_id varchar(250) PRIMARY KEY,
    age integer NULL,
    age_units varchar(250) NULL,
    health_status varchar(250) NULL,
    sex varchar(250) NULL,
    more_info varchar(250) NULL
);

DROP TABLE IF EXISTS files;
CREATE TABLE files (
    file_id varchar(250) PRIMARY KEY,
    data_source varchar(250) NOT NULL,
    assay_type varchar(250) NOT NULL,
    reference_genome_assembly varchar(250) NULL,
    data_quality_category varchar(250) NOT NULL,
    biosample_term_id varchar(250) NOT NULL,
    biosample_type varchar(250) NOT NULL,
    biosamples varchar(250)[] NOT NULL,
    cell_type varchar(250) NOT NULL,
    date_file_created timestamp NOT NULL,
    derived_from_exp varchar(250)[] NOT NULL,
    derived_from_ref varchar(250)[] NOT NULL,
    donor_ids varchar(250)[] NOT NULL,
    experiments varchar(250)[] NOT NULL,
    file_format varchar(250) NOT NULL,
    file_size_MB double precision NULL,
    file_format_subtype varchar(250) NOT NULL,
    file_available_in_gcs boolean NOT NULL,
    labs_generating_data varchar(250)[] NOT NULL,
    DNA_library_ids varchar(250)[] NOT NULL,
    md5sum varchar(250) NOT NULL,
    data_type varchar(250) NOT NULL,
    paired_end_sequencing boolean NULL,
    read_count bigint NULL,
    replicate_ids uuid[] NOT NULL,
    target_of_assay varchar(250) NULL,
    more_info varchar(250) NOT NULL,
    data_review_summary varchar(250)[] NOT NULL,
    file_gs_path varchar(250) NULL,
    read_length bigint NULL,
    percent_aligned_reads double precision NULL,
    percent_duplicated_reads double precision NULL
);
