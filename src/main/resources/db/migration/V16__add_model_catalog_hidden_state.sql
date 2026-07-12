alter table model_connection_models
    add column hidden_at timestamp;

create index idx_model_connection_models_visible
    on model_connection_models(connection_id, model_name)
    where hidden_at is null;
