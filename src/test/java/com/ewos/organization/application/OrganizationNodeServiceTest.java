package com.ewos.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.company.infrastructure.persistence.TenantRepository;
import com.ewos.organization.api.dto.CreateOrganizationNodeRequest;
import com.ewos.organization.api.dto.MergeNodeRequest;
import com.ewos.organization.api.dto.MoveNodeRequest;
import com.ewos.organization.api.dto.OrganizationNodeResponse;
import com.ewos.organization.api.dto.RenameNodeRequest;
import com.ewos.organization.domain.NodeChangeType;
import com.ewos.organization.domain.OrganizationLevel;
import com.ewos.organization.domain.OrganizationNode;
import com.ewos.organization.domain.OrganizationNodeVersion;
import com.ewos.organization.infrastructure.persistence.OrganizationLevelRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeVersionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrganizationNodeServiceTest {

    @Mock OrganizationNodeRepository nodeRepository;
    @Mock OrganizationLevelRepository levelRepository;
    @Mock OrganizationNodeVersionRepository versionRepository;
    @Mock TenantRepository tenantRepository;

    private OrganizationNodeService service;
    private Tenant tenant;
    private OrganizationLevel level;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setCode("DEFAULT");
        lenient().when(tenantRepository.findByCode("DEFAULT")).thenReturn(Optional.of(tenant));
        lenient().when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        level = new OrganizationLevel();
        level.setId(UUID.randomUUID());
        level.setTenant(tenant);
        level.setCode("BU");
        level.setName("Business Unit");
        level.setDisplaySequence(1);
        lenient().when(levelRepository.findById(level.getId())).thenReturn(Optional.of(level));

        lenient()
                .when(nodeRepository.save(any(OrganizationNode.class)))
                .thenAnswer(
                        inv -> {
                            OrganizationNode n = inv.getArgument(0);
                            if (n.getId() == null) {
                                n.setId(UUID.randomUUID());
                            }
                            return n;
                        });
        lenient()
                .when(versionRepository.save(any(OrganizationNodeVersion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service =
                new OrganizationNodeService(
                        nodeRepository,
                        levelRepository,
                        versionRepository,
                        new TenantResolver(tenantRepository));
    }

    @Test
    void createNodeWritesCreatedVersion() {
        CreateOrganizationNodeRequest req =
                new CreateOrganizationNodeRequest(
                        null, level.getId(), null, "N1", "Node 1", LocalDate.of(2026, 1, 1));
        when(nodeRepository.findByTenantAndCode(tenant, "N1")).thenReturn(Optional.empty());

        OrganizationNodeResponse resp = service.create(req);

        assertThat(resp.code()).isEqualTo("N1");
        assertThat(resp.parentNodeId()).isNull();

        ArgumentCaptor<OrganizationNodeVersion> captor =
                ArgumentCaptor.forClass(OrganizationNodeVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo(NodeChangeType.CREATED);
    }

    @Test
    void createNodeRejectsDuplicateCode() {
        OrganizationNode existing = existingNode("N1", null);
        when(nodeRepository.findByTenantAndCode(tenant, "N1")).thenReturn(Optional.of(existing));

        CreateOrganizationNodeRequest req =
                new CreateOrganizationNodeRequest(
                        null, level.getId(), null, "N1", "Node 1", LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void renameWritesRenamedVersionAndUpdatesName() {
        OrganizationNode node = existingNode("N1", null);
        when(nodeRepository.findById(node.getId())).thenReturn(Optional.of(node));

        service.rename(
                node.getId(), new RenameNodeRequest("Renamed", LocalDate.of(2026, 2, 1), null));

        assertThat(node.getName()).isEqualTo("Renamed");
        ArgumentCaptor<OrganizationNodeVersion> captor =
                ArgumentCaptor.forClass(OrganizationNodeVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo(NodeChangeType.RENAMED);
    }

    @Test
    void moveRejectsCycles() {
        OrganizationNode root = existingNode("R", null);
        OrganizationNode mid = existingNode("M", root);
        OrganizationNode leaf = existingNode("L", mid);

        when(nodeRepository.findById(root.getId())).thenReturn(Optional.of(root));
        when(nodeRepository.findById(leaf.getId())).thenReturn(Optional.of(leaf));

        // Try to make root's parent = leaf → cycle
        MoveNodeRequest req = new MoveNodeRequest(leaf.getId(), LocalDate.of(2026, 2, 1), null);

        assertThatThrownBy(() -> service.move(root.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void moveReparentsAndWritesMovedVersion() {
        OrganizationNode oldParent = existingNode("OP", null);
        OrganizationNode newParent = existingNode("NP", null);
        OrganizationNode node = existingNode("N", oldParent);

        when(nodeRepository.findById(node.getId())).thenReturn(Optional.of(node));
        when(nodeRepository.findById(newParent.getId())).thenReturn(Optional.of(newParent));

        service.move(
                node.getId(),
                new MoveNodeRequest(newParent.getId(), LocalDate.of(2026, 3, 1), null));

        assertThat(node.getParent()).isEqualTo(newParent);
    }

    @Test
    void mergeDeactivatesSourceAndReparentsChildren() {
        OrganizationNode source = existingNode("S", null);
        OrganizationNode target = existingNode("T", null);
        OrganizationNode child = existingNode("C", source);

        when(nodeRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(nodeRepository.findByParent(source)).thenReturn(List.of(child));

        service.mergeInto(
                source.getId(),
                new MergeNodeRequest(target.getId(), LocalDate.of(2026, 4, 1), "Q2 reorg"));

        assertThat(source.isActive()).isFalse();
        assertThat(source.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(child.getParent()).isEqualTo(target);
    }

    @Test
    void mergeRejectsSelfMerge() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.mergeInto(
                                        id,
                                        new MergeNodeRequest(id, LocalDate.of(2026, 4, 1), null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private OrganizationNode existingNode(String code, OrganizationNode parent) {
        OrganizationNode n = new OrganizationNode();
        n.setId(UUID.randomUUID());
        n.setTenant(tenant);
        n.setLevel(level);
        n.setParent(parent);
        n.setCode(code);
        n.setName(code);
        n.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        n.setActive(true);
        return n;
    }
}
