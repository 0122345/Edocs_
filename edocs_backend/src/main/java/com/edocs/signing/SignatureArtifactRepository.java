package com.edocs.signing;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SignatureArtifactRepository extends MongoRepository<SignatureArtifact, String> {
}
