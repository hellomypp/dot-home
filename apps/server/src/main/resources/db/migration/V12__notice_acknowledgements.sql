CREATE TABLE notice_acknowledgement (
 id VARCHAR(36) PRIMARY KEY,
 request_id VARCHAR(36) NOT NULL,
 member_id VARCHAR(36) NOT NULL,
 acknowledged_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_notice_ack_request FOREIGN KEY (request_id) REFERENCES household_request(id),
 CONSTRAINT fk_notice_ack_member FOREIGN KEY (member_id) REFERENCES membership(id),
 CONSTRAINT uq_notice_ack UNIQUE(member_id,request_id)
);
