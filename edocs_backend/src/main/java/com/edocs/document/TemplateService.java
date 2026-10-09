package com.edocs.document;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.config.CacheConfig;
import com.edocs.document.DocumentDtos.TemplateDto;
import com.edocs.identity.OrganizationRepository;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;

@Service
public class TemplateService {

    private final TemplateRepository templates;
    private final OrganizationRepository organizations;

    public TemplateService(TemplateRepository templates, OrganizationRepository organizations) {
        this.templates = templates;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.TEMPLATES, key = "#orgId")
    public List<TemplateDto> list(UUID orgId) {
        return templates.findAllInOrg(orgId).stream().map(DocumentMapper::template).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TEMPLATES, allEntries = true)
    public TemplateDto create(String name, String category) {
        AuthUser me = CurrentUser.get();
        String cat = category == null || category.isBlank() ? "General" : category.trim();
        Template t = templates.save(new Template(organizations.getReferenceById(me.orgId()), name.trim(), cat, DocumentService.BLANK_CONTENT, List.of()));
        return DocumentMapper.template(t);
    }
}
