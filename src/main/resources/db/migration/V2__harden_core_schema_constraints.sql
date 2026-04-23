alter table documents
    add constraint uk_documents_id_group unique (id, group_id);

alter table document_chunks
    add constraint uk_document_chunks_document_chunk unique (document_id, chunk_index);

alter table document_chunks
    drop constraint fk_document_chunks_document,
    drop constraint fk_document_chunks_group,
    add constraint fk_document_chunks_document_group
        foreign key (document_id, group_id) references documents (id, group_id);

alter table ingestion_jobs
    drop constraint fk_ingestion_jobs_document,
    drop constraint fk_ingestion_jobs_group,
    add constraint fk_ingestion_jobs_document_group
        foreign key (document_id, group_id) references documents (id, group_id);
