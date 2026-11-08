package com.classforge.activity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.UUID;
public interface ProjectActivityRepository extends MongoRepository<ProjectActivityEntity, UUID> { }
