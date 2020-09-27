
package org.openrefine.browsing.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import org.openrefine.browsing.facets.FacetConfigResolver;
import org.openrefine.browsing.facets.ScatterplotFacet.Dimension;
import org.openrefine.browsing.facets.ScatterplotFacet.Rotation;
import org.openrefine.browsing.facets.ScatterplotFacet.ScatterplotFacetConfig;
import org.openrefine.browsing.filters.AnyRowRecordFilter;
import org.openrefine.expr.MetaParser;
import org.openrefine.grel.Parser;
import org.openrefine.model.Cell;
import org.openrefine.model.Row;
import org.openrefine.model.RowFilter;
import org.openrefine.util.ParsingUtilities;

public class ScatterplotFacetAggregatorTests {

    public static String configJson = "{\n" +
            "          \"to_x\": 1,\n" +
            "          \"to_y\": 1,\n" +
            "          \"dot\": 1,\n" +
            "          \"from_x\": 20," +
            "          \"to_x\": 25," +
            "          \"from_y\": -10,\n" +
            "          \"to_y\": -3,\n" +
            "          \"l\": 150,\n" +
            "          \"type\": \"core/scatterplot\",\n" +
            "          \"dim_y\": \"lin\",\n" +
            "          \"ex\": \"value\",\n" +
            "          \"dim_x\": \"lin\",\n" +
            "          \"ey\": \"value\",\n" +
            "          \"cx\": \"my column\",\n" +
            "          \"cy\": \"e\",\n" +
            "          \"name\": \"my column (x) vs. e (y)\"\n" +
            "        }";

    ScatterplotFacetConfig config;
    ScatterplotFacetAggregator SUT;
    ScatterplotFacetState genericState;
    RowEvaluable evalX;
    RowEvaluable evalY;

    @BeforeTest
    public void setUp() throws JsonParseException, JsonMappingException, IOException {
        FacetConfigResolver.registerFacetConfig("core", "scatterplot", ScatterplotFacetConfig.class);
        MetaParser.registerLanguageParser("grel", "GREL", Parser.grelParser, "value");
        config = ParsingUtilities.mapper.readValue(configJson, ScatterplotFacetConfig.class);
        evalX = new RowEvaluable() {

            private static final long serialVersionUID = 1L;

            @Override
            public Object eval(long rowIndex, Row row, Properties bindings) {
                return row.getCellValue(0);
            }

        };
        evalY = new RowEvaluable() {

            private static final long serialVersionUID = 1L;

            @Override
            public Object eval(long rowIndex, Row row, Properties bindings) {
                return row.getCellValue(1);
            }

        };
        SUT = new ScatterplotFacetAggregator(config, evalX, evalY, Rotation.NO_ROTATION, 1, Dimension.LIN, Dimension.LIN);

        genericState = new ScatterplotFacetState(
                new double[] { 3.45, 89.7, -37, 0 },
                new double[] { -90, 1.3, 378, 0 },
                new boolean[] { true, true, false, false }, 3);
    }

    @Test
    public void testWithRow() {
        ScatterplotFacetState state = SUT.withRow(genericState, 1234L, new Row(Arrays.asList(new Cell(23.3, null), new Cell(-7, null))));

        ScatterplotFacetState expectedState = new ScatterplotFacetState(
                new double[] { 3.45, 89.7, -37, 23.3 },
                new double[] { -90, 1.3, 378, -7 },
                new boolean[] { true, true, false, true }, 4);

        Assert.assertEquals(state, expectedState);
    }

    @Test
    public void testWithRowOutsideView() {
        ScatterplotFacetState state = SUT.withRowOutsideView(genericState, 1234L,
                new Row(Arrays.asList(new Cell(23.3, null), new Cell(-7, null))));

        ScatterplotFacetState expectedState = new ScatterplotFacetState(
                new double[] { 3.45, 89.7, -37, 23.3 },
                new double[] { -90, 1.3, 378, -7 },
                new boolean[] { true, true, false, false }, 4);

        Assert.assertEquals(state, expectedState);
    }

    @Test
    public void testWithRowNonNumeric() {
        ScatterplotFacetState state = SUT.withRow(genericState, 1234L,
                new Row(Arrays.asList(new Cell("non-numeric", null), new Cell(-7, null))));

        Assert.assertEquals(state, genericState);
    }

    @Test
    public void testWithNaN() {
        ScatterplotFacetState state = SUT.withRow(genericState, 1234L,
                new Row(Arrays.asList(new Cell(34, null), new Cell(Double.NaN, null))));

        Assert.assertEquals(state, genericState);
    }

    @Test
    public void testSum() {
        ScatterplotFacetState sum = SUT.sum(genericState, genericState);

        ScatterplotFacetState expectedState = new ScatterplotFacetState(
                new double[] { 3.45, 89.7, -37, 3.45, 89.7, -37 },
                new double[] { -90, 1.3, 378, -90, 1.3, 378 },
                new boolean[] { true, true, false, true, true, false }, 6);

        Assert.assertEquals(sum, expectedState);
    }

    @Test
    public void testRowFilter() {
        RowFilter filter = SUT.getRowFilter();

        Assert.assertTrue(filter.filterRow(1234L, new Row(Arrays.asList(new Cell(23.3, null), new Cell(-7, null)))));
        Assert.assertFalse(filter.filterRow(1234L, new Row(Arrays.asList(new Cell(-8, null), new Cell(34, null)))));
    }

    @Test
    public void testRecordFilter() {
        Assert.assertTrue(SUT.getRecordFilter() instanceof AnyRowRecordFilter);
    }

    @Test
    public void testRotation() {
        ScatterplotFacetAggregator withRotation = new ScatterplotFacetAggregator(
                config, evalX, evalY, Rotation.ROTATE_CCW, 1, Dimension.LIN, Dimension.LIN);

        ScatterplotFacetState state = withRotation.withRow(genericState, 1234L,
                new Row(Arrays.asList(new Cell(23.3, null), new Cell(-7, null))));
        ScatterplotFacetState expectedState = new ScatterplotFacetState(
                new double[] { 3.45, 89.7, -37, 15.649999999999999 },
                new double[] { -90, 1.3, 378, 8.149999999999999 },
                new boolean[] { true, true, false, true }, 4);

        Assert.assertEquals(state, expectedState);
    }
}