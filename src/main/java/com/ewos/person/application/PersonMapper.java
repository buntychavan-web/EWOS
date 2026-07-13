package com.ewos.person.application;

import com.ewos.person.api.dto.AddressResponse;
import com.ewos.person.api.dto.ContactResponse;
import com.ewos.person.api.dto.DuplicateRuleResponse;
import com.ewos.person.api.dto.EducationResponse;
import com.ewos.person.api.dto.EmergencyContactResponse;
import com.ewos.person.api.dto.FamilyMemberResponse;
import com.ewos.person.api.dto.IdentityDocumentResponse;
import com.ewos.person.api.dto.PersonResponse;
import com.ewos.person.api.dto.PersonVersionResponse;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonAddress;
import com.ewos.person.domain.PersonContact;
import com.ewos.person.domain.PersonDuplicateRule;
import com.ewos.person.domain.PersonEducation;
import com.ewos.person.domain.PersonEmergencyContact;
import com.ewos.person.domain.PersonFamilyMember;
import com.ewos.person.domain.PersonIdentityDocument;
import com.ewos.person.domain.PersonVersion;

/** Pure entity → response mappers. */
final class PersonMapper {

    private PersonMapper() {}

    static PersonVersionResponse toVersion(PersonVersion v) {
        if (v == null) {
            return null;
        }
        return new PersonVersionResponse(
                v.getId(),
                v.getVersionNumber(),
                v.getEffectiveFrom(),
                v.getEffectiveTo(),
                v.getFirstName(),
                v.getMiddleName(),
                v.getLastName(),
                v.getPreferredName(),
                v.getGender(),
                v.getDateOfBirth(),
                v.getMaritalStatus(),
                v.getBloodGroup(),
                v.getNationality(),
                v.getPhotoUrl(),
                v.getChangeReason(),
                v.getApprovedBy(),
                v.getCreatedAt(),
                v.getUpdatedAt(),
                v.getCreatedBy(),
                v.getUpdatedBy(),
                v.getVersion());
    }

    static PersonResponse toPerson(Person p, PersonVersion current) {
        return new PersonResponse(
                p.getId(),
                p.getTenant().getId(),
                p.getGroupPersonId(),
                p.isActive(),
                toVersion(current),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getCreatedBy(),
                p.getUpdatedBy(),
                p.getVersion());
    }

    static ContactResponse toContact(PersonContact c) {
        return new ContactResponse(
                c.getId(),
                c.getPerson().getId(),
                c.getPersonalMobile(),
                c.getAlternateMobile(),
                c.getPersonalEmail(),
                c.getAlternateEmail(),
                c.getEffectiveFrom(),
                c.getEffectiveTo(),
                c.getVersion());
    }

    static AddressResponse toAddress(PersonAddress a) {
        return new AddressResponse(
                a.getId(),
                a.getPerson().getId(),
                a.getAddressKind(),
                a.getLine1(),
                a.getLine2(),
                a.getCity(),
                a.getState(),
                a.getCountry(),
                a.getPostalCode(),
                a.getEffectiveFrom(),
                a.getEffectiveTo(),
                a.getVersion());
    }

    static EmergencyContactResponse toEmergency(PersonEmergencyContact e) {
        return new EmergencyContactResponse(
                e.getId(),
                e.getPerson().getId(),
                e.getName(),
                e.getRelationship(),
                e.getPriority(),
                e.getMobile(),
                e.getAlternateMobile(),
                e.getEmail(),
                e.getAddress(),
                e.getVersion());
    }

    static FamilyMemberResponse toFamily(PersonFamilyMember f) {
        return new FamilyMemberResponse(
                f.getId(),
                f.getPerson().getId(),
                f.getRelation(),
                f.getName(),
                f.getDateOfBirth(),
                f.getGender(),
                f.getOccupation(),
                f.isDependent(),
                f.getMobile(),
                f.getEmail(),
                f.getVersion());
    }

    static EducationResponse toEducation(PersonEducation e) {
        return new EducationResponse(
                e.getId(),
                e.getPerson().getId(),
                e.getQualification(),
                e.getInstitution(),
                e.getPassingYear(),
                e.getGrade(),
                e.getSpecialization(),
                e.getDocumentUrl(),
                e.getVersion());
    }

    static IdentityDocumentResponse toDocument(PersonIdentityDocument d) {
        return new IdentityDocumentResponse(
                d.getId(),
                d.getPerson().getId(),
                d.getDocumentKind(),
                d.getDocumentNumber(),
                d.getIssuedBy(),
                d.getIssuedOn(),
                d.getExpiresOn(),
                d.getDocumentUrl(),
                d.getEffectiveFrom(),
                d.getEffectiveTo(),
                d.isVerified(),
                d.getVersion());
    }

    static DuplicateRuleResponse toRule(PersonDuplicateRule r) {
        return new DuplicateRuleResponse(
                r.getId(),
                r.getTenant().getId(),
                r.getRuleKind(),
                r.isEnabled(),
                r.getWeight(),
                r.getVersion());
    }
}
