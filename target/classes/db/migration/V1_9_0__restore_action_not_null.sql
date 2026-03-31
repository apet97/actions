ALTER TABLE actions
    ALTER COLUMN http_method SET NOT NULL,
    ALTER COLUMN url_template SET NOT NULL;
