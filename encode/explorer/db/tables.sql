CREATE TABLE donors (
    donor_id varchar(250) PRIMARY KEY,
    age integer NULL,
    age_units varchar(250) NULL,
    health_status varchar(250) NULL,
    sex varchar(250) NULL,
    more_info varchar(250) NULL
);

CREATE INDEX donors_age ON donors (age);
CREATE INDEX donors_age_units ON donors (age_units);
CREATE INDEX donors_health_status ON donors (health_status);
CREATE INDEX donors_sex ON donors (sex);

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
    file_size_mb double precision NULL,
    file_format_subtype varchar(250) NOT NULL,
    file_available_in_gcs boolean NOT NULL,
    labs_generating_data varchar(250)[] NOT NULL,
    dna_library_ids varchar(250)[] NOT NULL,
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

CREATE INDEX files_assay_type ON files (assay_type);
CREATE INDEX files_reference_genome_assembly ON files (reference_genome_assembly);
CREATE INDEX files_data_quality_category ON files (data_quality_category);
CREATE INDEX files_biosample_type ON files (biosample_type);
CREATE INDEX files_cell_type ON files (cell_type);
CREATE INDEX files_donor_ids ON files USING gin (donor_ids);
CREATE INDEX files_file_format ON files (file_format);
CREATE INDEX files_file_size_mb ON files (file_size_mb);
CREATE INDEX files_file_format_subtype ON files (file_format_subtype);
CREATE INDEX files_file_available_in_gcs ON files (file_available_in_gcs);
CREATE INDEX files_labs_generating_data ON files USING gin (labs_generating_data);
CREATE INDEX files_dna_library_ids ON files USING gin (dna_library_ids);
CREATE INDEX files_data_type ON files (data_type);
CREATE INDEX files_paired_end_sequencing ON files (paired_end_sequencing);
CREATE INDEX files_read_count ON files (read_count);
CREATE INDEX files_target_of_assay ON files (target_of_assay);
CREATE INDEX files_read_length ON files (read_length);
CREATE INDEX files_percent_aligned_reads ON files (percent_aligned_reads);
CREATE INDEX files_percent_percent_duplicated_reads ON files (percent_duplicated_reads);
