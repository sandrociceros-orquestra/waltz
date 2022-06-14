package org.finos.waltz.web.endpoints.extracts.dynamic;

import org.finos.waltz.model.application.LifecyclePhase;
import org.finos.waltz.model.report_grid.ReportGrid;
import org.finos.waltz.model.report_grid.ReportGridColumnDefinition;
import org.finos.waltz.model.report_grid.ReportSubject;
import org.finos.waltz.web.endpoints.api.BookmarksEndpoint;
import org.finos.waltz.web.endpoints.extracts.ExtractFormat;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.finos.waltz.common.ListUtilities.*;
import static org.jooq.lambda.fi.util.function.CheckedConsumer.unchecked;

import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;

@Component
public class DynamicCommaSeperatedValueFormatter implements DynamicFormatter {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicCommaSeperatedValueFormatter.class);
    private final FormatterUtils formatterUtils;

    public DynamicCommaSeperatedValueFormatter(FormatterUtils formatterUtils){
        this.formatterUtils = formatterUtils;
    }


    @Override
    public byte[] format(String id,
                         ReportGrid reportGrid,
                         List<Tuple2<ReportGridColumnDefinition, Boolean>> columnDefinitions,
                         List<Tuple2<ReportSubject, ArrayList<Object>>> reportRows) throws IOException{
        try {
            LOG.info("Generating CSV report {}",id);
            return mkCSVReport(columnDefinitions,reportRows);
        } catch (IOException e) {
           LOG.warn("Encounter error when trying to generate CSV report.  Details:{}", e.getMessage());
           throw e;
        }
    }

    private byte[] mkCSVReport(List<Tuple2<ReportGridColumnDefinition, Boolean>> columnDefinitions,
                               List<Tuple2<ReportSubject, ArrayList<Object>>> reportRows) throws IOException {
        List<String> headers = formatterUtils.mkHeaderStrings(columnDefinitions);

        StringWriter writer = new StringWriter();
        CsvListWriter csvWriter = new CsvListWriter(writer, CsvPreference.EXCEL_PREFERENCE);

        csvWriter.write(headers);
        reportRows.forEach(unchecked(row -> csvWriter.write(simplify(row))));
        csvWriter.flush();

        return writer.toString().getBytes();
    }


    private List<Object> simplify(Tuple2<ReportSubject, ArrayList<Object>> row) {

        long appId = row.v1.entityReference().id();
        String appName = row.v1.entityReference().name().get();
        Optional<String> assetCode = row.v1.entityReference().externalId();
        LifecyclePhase lifecyclePhase = row.v1.lifecyclePhase();

        List<Object> appInfo = asList(appId, appName, assetCode, lifecyclePhase.name());

        return map(concat(appInfo, row.v2), value -> {
            if (value == null) return null;
            if (value instanceof Optional) {
                return ((Optional<?>) value).orElse(null);
            } else {
                return value;
            }
        });
    }


}
