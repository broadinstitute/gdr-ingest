FROM continuumio/miniconda

LABEL maintainer="DSP Data Centers <monster@broadinstitute.org>"

ARG SAMTOOLS_VERSION

# add channels in correct order to avoid missing libcrypto dependency
# https://github.com/bioconda/bioconda-recipes/issues/12100
RUN conda config --add channels defaults && \
      conda config --add channels bioconda && \
      conda config --add channels conda-forge && \
      conda install -q -y samtools==${SAMTOOLS_VERSION} && \
      conda install -c conda-forge google-cloud-sdk && \ 
      conda clean --all -y

