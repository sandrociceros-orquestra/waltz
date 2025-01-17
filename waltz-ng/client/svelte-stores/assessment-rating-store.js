import {remote} from "./remote";


export function mkAssessmentRatingStore() {
    const findByDefinitionId = (id) => remote
        .fetchViewData(
            "GET",
            `api/assessment-rating/definition-id/${id}`);

    const findByEntityKind = (kind, force = false) => remote
        .fetchViewList(
            "GET",
            `api/assessment-rating/entity-kind/${kind}`,
            null,
            {force});

    const findForEntityReference = (ref, force = false) => remote
        .fetchViewList(
            "GET",
            `api/assessment-rating/entity/${ref.kind}/${ref.id}`,
            null,
            {force});

    const store = (ref, defnId, rating) => remote
        .execute(
            "POST",
            `api/assessment-rating/entity/${ref.kind}/${ref.id}/${defnId}`,
            rating);

    const findRatingPermissions = (ref, defnId, force = false) => remote
        .fetchViewList(
            "GET",
            `api/assessment-rating/entity/${ref.kind}/${ref.id}/${defnId}/permissions`,
            null,
            {force});

    const lock = (ref, defnId, ratingId) => remote
        .execute(
            "PUT",
            `api/assessment-rating/entity/${ref.kind}/${ref.id}/${defnId}/${ratingId}/lock`,
            null);

    const unlock = (ref, defnId, ratingId) => remote
        .execute(
            "PUT",
            `api/assessment-rating/entity/${ref.kind}/${ref.id}/${defnId}/${ratingId}/unlock`,
            null);

    const remove = (ref, defnId, ratingId) => remote
        .execute(
            "DELETE",
            `api/assessment-rating/entity/${ref.kind}/${ref.id}/${defnId}/${ratingId}`);

    const updateComment = (id, comment) => remote
        .execute(
            "POST",
            `api/assessment-rating/id/${id}/update-comment`,
            {comment});


    const updateRating = (id, updateCmd) => remote
        .execute(
            "POST",
            `api/assessment-rating/id/${id}/update-rating`,
            updateCmd);

    const findSummaryCounts = (summaryRequest, targetKind, force = false) =>
        remote
            .fetchViewList(
                "POST",
                `api/assessment-rating/target-kind/${targetKind}/summary-counts`,
                summaryRequest,
                {force});

    const hasMultiValuedAssessments = (defnId, force = false) =>
        remote
            .fetchViewDatum(
                "GET",
                `api/assessment-rating/definition-id/${defnId}/mva-check`,
                null,
                {force});

    const bulkPreview = (entityRef, rawText) =>
        remote
            .execute(
                "POST",
                `api/assessment-rating/bulk/preview/${entityRef.kind}/${entityRef.id}`,
                rawText);

    const bulkApply = (entityRef, rawText) =>
        remote
            .execute(
                "POST",
                `api/assessment-rating/bulk/apply/${entityRef.kind}/${entityRef.id}`,
                rawText);

    return {
        findByDefinitionId,
        findForEntityReference,
        findByEntityKind,
        findRatingPermissions,
        store,
        remove,
        lock,
        unlock,
        updateComment,
        updateRating,
        findSummaryCounts,
        hasMultiValuedAssessments,
        bulkPreview,
        bulkApply
    };
}


export const assessmentRatingStore = mkAssessmentRatingStore();