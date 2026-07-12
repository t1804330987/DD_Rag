-- Allow AES-encrypted API key storage while keeping the existing column name for minimal code churn.
alter table model_connections
    drop constraint if exists ck_model_connections_credential_storage;

alter table model_connections
    add constraint ck_model_connections_credential_storage
        check (credential_storage_type in ('PLAINTEXT', 'ENCRYPTED'));
