package org.finos.waltz.service.entity_relationship;

import org.finos.waltz.common.SetUtilities;
import org.finos.waltz.model.bulk_upload.entity_relationship.BulkUploadRelationshipItem;
import org.finos.waltz.model.bulk_upload.entity_relationship.BulkUploadRelationshipParsedResult;
import org.finos.waltz.model.bulk_upload.entity_relationship.ImmutableBulkUploadRelationshipItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.finos.waltz.common.IOUtilities.readAsString;
import static org.junit.jupiter.api.Assertions.*;

public class BulkUploadRelationshipItemParserTest {

    private final BulkUploadRelationshipItemParser parser = new BulkUploadRelationshipItemParser();


    private String readTestFile(String fileName) {
        return readAsString(BulkUploadRelationshipItemParserTest.class.getResourceAsStream(fileName));
    }

    private List<BulkUploadRelationshipItem> getParsedItems() {
        List<BulkUploadRelationshipItem> parsedItems = new ArrayList<BulkUploadRelationshipItem>();
        parsedItems.add(ImmutableBulkUploadRelationshipItem
                .builder()
                .sourceExternalId("sourceExtA")
                .targetExternalId("tarExtB")
                .description("desc changes")
                .build());
        parsedItems.add(ImmutableBulkUploadRelationshipItem
                .builder()
                .sourceExternalId("sourceExtB")
                .targetExternalId("tarExtB")
                .description("desc chnages 2")
                .build());
        parsedItems.add(ImmutableBulkUploadRelationshipItem
                .builder()
                .sourceExternalId("sourceExtC")
                .targetExternalId("tarExtC")
                .description("desc changes")
                .build());

        return parsedItems;
    }


    @Test
    void simpleTSV() {
        /*
        Test to check whether the parser is parsing items
         */
        BulkUploadRelationshipParsedResult result = parser.parse(readTestFile("test-relationship-item.tsv"), BulkUploadRelationshipItemParser.InputFormat.TSV);
        assertEquals(null, result.error());
        assertEquals(3, result.parsedItems().size());

        Set<String> sourceExternalIds = SetUtilities.map(result.parsedItems(), BulkUploadRelationshipItem::sourceExternalId);
        assertEquals(sourceExternalIds, SetUtilities.asSet("sourceExtA", "sourceExtB", "sourceExtC"));
    }

    @Test
    void errorTSV() {
        /*
        Test to check whether the parser throws format exception
         */
        BulkUploadRelationshipParsedResult result = parser.parse(readTestFile("test-relationship-error-item.tsv"), BulkUploadRelationshipItemParser.InputFormat.TSV);
        assertNotNull(result.error());
    }
}
