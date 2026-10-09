package com.edocs.document;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DocumentContentRepository extends MongoRepository<DocumentContent, String> {

    List<DocumentContent> findByDocumentIdIn(List<String> ids);
}
