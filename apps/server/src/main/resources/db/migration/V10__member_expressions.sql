CREATE TABLE member_expression (
 id VARCHAR(36) PRIMARY KEY,
 member_id VARCHAR(36) NOT NULL,
 field_name VARCHAR(24) NOT NULL,
 expression_value VARCHAR(200),
 updated_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6),
 version BIGINT NOT NULL,
 CONSTRAINT fk_expression_member FOREIGN KEY (member_id) REFERENCES membership(id),
 CONSTRAINT uq_expression_member_field UNIQUE(member_id,field_name)
);

CREATE TABLE expression_command (
 id VARCHAR(36) PRIMARY KEY,
 member_id VARCHAR(36) NOT NULL,
 request_id VARCHAR(80) NOT NULL,
 body_hash VARCHAR(64) NOT NULL,
 response_json TEXT NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_expression_command_member FOREIGN KEY (member_id) REFERENCES membership(id),
 CONSTRAINT uq_expression_command UNIQUE(member_id,request_id)
);
CREATE INDEX ix_expression_command_expiry ON expression_command(expires_at);
