package com.vogella.services.innosensr;

import reactor.core.publisher.Mono;

public interface InnoSensrService {
	
	Mono<Void> createProject(String title, String description);

	Mono<Void> createRequirement(String projectUniqueKey, String title, String description, InnoSensrStatus status);
}
