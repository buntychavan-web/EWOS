package com.ewos.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.organization.api.dto.ResolvedInheritanceResponse;
import com.ewos.organization.api.dto.SetInheritanceOverrideRequest;
import com.ewos.organization.domain.InheritableKind;
import com.ewos.organization.domain.OrganizationInheritanceOverride;
import com.ewos.organization.domain.OrganizationLevel;
import com.ewos.organization.domain.OrganizationNode;
import com.ewos.organization.infrastructure.persistence.OrganizationInheritanceOverrideRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrganizationInheritanceServiceTest {

    @Mock OrganizationInheritanceOverrideRepository overrideRepository;
    @Mock OrganizationNodeRepository nodeRepository;

    private OrganizationInheritanceService service;
    private Tenant tenant;
    private OrganizationLevel level;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        level = new OrganizationLevel();
        level.setId(UUID.randomUUID());
        level.setTenant(tenant);
        level.setCode("BU");
        lenient()
                .when(overrideRepository.save(any(OrganizationInheritanceOverride.class)))
                .thenAnswer(
                        inv -> {
                            OrganizationInheritanceOverride o = inv.getArgument(0);
                            if (o.getId() == null) {
                                o.setId(UUID.randomUUID());
                            }
                            return o;
                        });

        service = new OrganizationInheritanceService(overrideRepository, nodeRepository);
    }

    @Test
    void resolveReturnsOverrideOnLeaf() {
        OrganizationNode leaf = node("L", null);
        UUID ref = UUID.randomUUID();
        OrganizationInheritanceOverride override =
                liveOverride(leaf, InheritableKind.LEAVE_POLICY, ref);
        when(nodeRepository.findById(leaf.getId())).thenReturn(Optional.of(leaf));
        when(overrideRepository.findByNodeAndInheritableKind(leaf, InheritableKind.LEAVE_POLICY))
                .thenReturn(List.of(override));

        ResolvedInheritanceResponse resp =
                service.resolve(
                        leaf.getId(), InheritableKind.LEAVE_POLICY, LocalDate.of(2026, 6, 1));

        assertThat(resp.fromOverride()).isTrue();
        assertThat(resp.overrideRef()).isEqualTo(ref);
        assertThat(resp.sourceNodeId()).isEqualTo(leaf.getId());
    }

    @Test
    void resolveWalksUpToParentOverride() {
        OrganizationNode root = node("R", null);
        OrganizationNode leaf = node("L", root);
        UUID ref = UUID.randomUUID();
        OrganizationInheritanceOverride override =
                liveOverride(root, InheritableKind.HOLIDAY_CALENDAR, ref);
        when(nodeRepository.findById(leaf.getId())).thenReturn(Optional.of(leaf));
        when(overrideRepository.findByNodeAndInheritableKind(
                        leaf, InheritableKind.HOLIDAY_CALENDAR))
                .thenReturn(List.of());
        when(overrideRepository.findByNodeAndInheritableKind(
                        root, InheritableKind.HOLIDAY_CALENDAR))
                .thenReturn(List.of(override));

        ResolvedInheritanceResponse resp =
                service.resolve(
                        leaf.getId(), InheritableKind.HOLIDAY_CALENDAR, LocalDate.of(2026, 6, 1));

        assertThat(resp.fromOverride()).isTrue();
        assertThat(resp.sourceNodeId()).isEqualTo(root.getId());
        assertThat(resp.overrideRef()).isEqualTo(ref);
    }

    @Test
    void resolveReturnsUnresolvedWhenNothingInChain() {
        OrganizationNode root = node("R", null);
        OrganizationNode leaf = node("L", root);
        when(nodeRepository.findById(leaf.getId())).thenReturn(Optional.of(leaf));
        when(overrideRepository.findByNodeAndInheritableKind(any(), any())).thenReturn(List.of());

        ResolvedInheritanceResponse resp =
                service.resolve(
                        leaf.getId(), InheritableKind.SHIFT_POLICY, LocalDate.of(2026, 6, 1));

        assertThat(resp.fromOverride()).isFalse();
        assertThat(resp.sourceNodeId()).isNull();
        assertThat(resp.overrideRef()).isNull();
    }

    @Test
    void setOverrideRejectsOverlap() {
        OrganizationNode leaf = node("L", null);
        when(nodeRepository.findById(leaf.getId())).thenReturn(Optional.of(leaf));
        OrganizationInheritanceOverride existing =
                liveOverride(leaf, InheritableKind.LEAVE_POLICY, UUID.randomUUID());
        existing.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        existing.setEffectiveTo(LocalDate.of(2026, 12, 31));
        when(overrideRepository.findByNodeAndInheritableKind(leaf, InheritableKind.LEAVE_POLICY))
                .thenReturn(List.of(existing));

        SetInheritanceOverrideRequest req =
                new SetInheritanceOverrideRequest(
                        InheritableKind.LEAVE_POLICY,
                        UUID.randomUUID(),
                        null,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2027, 6, 1));

        assertThatThrownBy(() -> service.setOverride(leaf.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private OrganizationNode node(String code, OrganizationNode parent) {
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

    private OrganizationInheritanceOverride liveOverride(
            OrganizationNode node, InheritableKind kind, UUID ref) {
        OrganizationInheritanceOverride o = new OrganizationInheritanceOverride();
        o.setId(UUID.randomUUID());
        o.setNode(node);
        o.setInheritableKind(kind);
        o.setOverrideRef(ref);
        o.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        o.setEffectiveTo(null);
        return o;
    }
}
