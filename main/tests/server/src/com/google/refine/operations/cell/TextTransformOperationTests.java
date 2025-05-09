
package com.google.refine.operations.cell;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.node.TextNode;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.refine.RefineTest;
import com.google.refine.browsing.EngineConfig;
import com.google.refine.expr.EvalError;
import com.google.refine.expr.MetaParser;
import com.google.refine.grel.Parser;
import com.google.refine.model.ColumnsDiff;
import com.google.refine.model.Project;
import com.google.refine.operations.OnError;
import com.google.refine.operations.OperationDescription;
import com.google.refine.operations.OperationRegistry;
import com.google.refine.util.ParsingUtilities;
import com.google.refine.util.TestUtils;

public class TextTransformOperationTests extends RefineTest {

    @BeforeSuite
    public void registerOperation() {
        OperationRegistry.registerOperation(getCoreModule(), "text-transform", TextTransformOperation.class);
    }

    @BeforeMethod
    public void registerGRELParser() {
        MetaParser.registerLanguageParser("grel", "GREL", Parser.grelParser, "value");
    }

    @AfterMethod
    public void unregisterGRELParser() {
        MetaParser.unregisterLanguageParser("grel");
    }

    protected Project project;

    @BeforeMethod
    public void setUpInitialState() {
        project = createProject(new String[] { "foo", "bar", "hello" },
                new Serializable[][] {
                        { "v1", "a", "d" },
                        { "v3", "a", "f" },
                        { "", "a", "g" },
                        { "", "b", "h" },
                        { new EvalError("error"), "a", "i" },
                        { "v1", "b", "j" }
                });
    }

    @Test
    public void serializeTransformOperation() throws Exception {
        String description = OperationDescription.cell_text_transform_brief("organization_json",
                "grel:value.parseJson()[\"employment-summary\"].join('###')");
        String json = "{"
                + "   \"op\":\"core/text-transform\","
                + "   \"description\":" + new TextNode(description).toString() + ","
                + "   \"engineConfig\":{\"mode\":\"row-based\",\"facets\":[]},"
                + "   \"columnName\":\"organization_json\","
                + "   \"expression\":\"grel:value.parseJson()[\\\"employment-summary\\\"].join('###')\","
                + "   \"onError\":\"set-to-blank\","
                + "   \"repeat\": false,"
                + "   \"repeatCount\": 0"
                + "}";
        TestUtils.isSerializedTo(ParsingUtilities.mapper.readValue(json, TextTransformOperation.class), json);
    }

    @Test
    public void testValidate() {
        assertThrows(IllegalArgumentException.class, () -> new TextTransformOperation(
                invalidEngineConfig,
                "bar",
                "grel:cells[\"foo\"].value+'_'+value",
                OnError.SetToBlank,
                false, 0).validate());
        assertThrows(IllegalArgumentException.class, () -> new TextTransformOperation(
                defaultEngineConfig,
                null,
                "grel:cells[\"foo\"].value+'_'+value",
                OnError.SetToBlank,
                false, 0).validate());
        assertThrows(IllegalArgumentException.class, () -> new TextTransformOperation(
                defaultEngineConfig,
                "bar",
                "grel:foo(",
                OnError.SetToBlank,
                false, 0).validate());
    }

    @Test
    public void testColumnsDiff() {
        assertEquals(new TextTransformOperation(
                defaultEngineConfig,
                "bar",
                "grel:cells[\"foo\"].value+'_'+value",
                OnError.SetToBlank,
                false, 0).getColumnsDiff().get(), ColumnsDiff.modifySingleColumn("bar"));
    }

    @Test
    public void testColumnsDependencies() {
        assertEquals(new TextTransformOperation(
                engineConfigWithColumnDeps,
                "bar",
                "grel:cells[\"foo\"].value+'_'+value",
                OnError.SetToBlank,
                false, 0).getColumnDependencies().get(), Set.of("foo", "bar", "facet_1"));
    }

    @Test
    public void testRename() {
        var SUT = new TextTransformOperation(
                engineConfigWithColumnDeps,
                "bar",
                "grel:cells[\"foo\"].value+'_'+value",
                OnError.SetToBlank,
                false, 0);

        TextTransformOperation renamed = SUT.renameColumns(Map.of("foo", "foo2", "bar", "bar2"));
        String description = OperationDescription.cell_text_transform_brief("bar2",
                "grel:cells.get(\"foo2\").value + '_' + value");
        String expectedJson = "{\n"
                + "  \"columnName\" : \"bar2\",\n"
                + "  \"description\" : " + new TextNode(description).toString() + ",\n"
                + "  \"engineConfig\" : null,\n"
                + "  \"expression\" : \"grel:cells.get(\\\"foo2\\\").value + '_' + value\",\n"
                + "  \"onError\" : \"set-to-blank\",\n"
                + "  \"op\" : \"core/text-transform\",\n"
                + "  \"repeat\" : false,\n"
                + "  \"repeatCount\" : 0\n"
                + "}";
        TestUtils.isSerializedTo(renamed, expectedJson);
    }

    @Test
    public void testTransformColumnInRowsMode() throws Exception {
        TextTransformOperation operation = new TextTransformOperation(
                EngineConfig.defaultRowBased(),
                "bar",
                "grel:cells[\"foo\"].value+'_'+value",
                OnError.SetToBlank,
                false, 0);

        runOperation(operation, project);

        Project expected = createProject(
                new String[] { "foo", "bar", "hello" },
                new Serializable[][] {
                        { "v1", "v1_a", "d" },
                        { "v3", "v3_a", "f" },
                        { "", "_a", "g" },
                        { "", "_b", "h" },
                        { new EvalError("error"), null, "i" },
                        { "v1", "v1_b", "j" }
                });
        assertProjectEquals(project, expected);
    }

    @Test
    public void testTransformIdentity() throws Exception {
        TextTransformOperation operation = new TextTransformOperation(
                EngineConfig.defaultRowBased(),
                "bar",
                "grel:value",
                OnError.SetToBlank,
                false, 0);

        runOperation(operation, project);

        Project expected = createProject(new String[] { "foo", "bar", "hello" },
                new Serializable[][] {
                        { "v1", "a", "d" },
                        { "v3", "a", "f" },
                        { "", "a", "g" },
                        { "", "b", "h" },
                        { new EvalError("error"), "a", "i" },
                        { "v1", "b", "j" }
                });
        assertProjectEquals(project, expected);
    }

    @Test
    public void testTransformNull() throws Exception {
        TextTransformOperation operation = new TextTransformOperation(
                EngineConfig.defaultRowBased(),
                "bar",
                "grel:null",
                OnError.SetToBlank,
                false, 0);

        runOperation(operation, project);

        Project expected = createProject(
                new String[] { "foo", "bar", "hello" },
                new Serializable[][] {
                        { "v1", null, "d" },
                        { "v3", null, "f" },
                        { "", null, "g" },
                        { "", null, "h" },
                        { new EvalError("error"), null, "i" },
                        { "v1", null, "j" }
                });
        assertProjectEquals(project, expected);
    }

    @Test
    public void testTransformColumnInRecordsMode() throws Exception {
        TextTransformOperation operation = new TextTransformOperation(
                EngineConfig.deserialize("{\"mode\":\"record-based\",\"facets\":[]}"),
                "bar",
                "grel:cells[\"foo\"].value+'_'+row.record.rowCount",
                OnError.SetToBlank,
                false, 0);

        runOperation(operation, project);

        Project expected = createProject(
                new String[] { "foo", "bar", "hello" },
                new Serializable[][] {
                        { "v1", "v1_1", "d" },
                        { "v3", "v3_4", "f" },
                        { "", "_4", "g" },
                        { "", "_4", "h" },
                        { new EvalError("error"), null, "i" },
                        { "v1", "v1_1", "j" }
                });
        assertProjectEquals(project, expected);
    }

    @Test
    public void testTransformColumnNonLocalOperationInRowsMode() throws Exception {
        TextTransformOperation operation = new TextTransformOperation(
                EngineConfig.deserialize("{\"mode\":\"record-based\",\"facets\":[]}"),
                "bar",
                "grel:value + '_' + facetCount(value, 'value', 'bar')",
                OnError.SetToBlank,
                false, 0);

        runOperation(operation, project);

        Project expected = createProject(
                new String[] { "foo", "bar", "hello" },
                new Serializable[][] {
                        { "v1", "a_4", "d" },
                        { "v3", "a_4", "f" },
                        { "", "a_4", "g" },
                        { "", "b_2", "h" },
                        { new EvalError("error"), "a_4", "i" },
                        { "v1", "b_2", "j" }
                });
        assertProjectEquals(project, expected);
    }

}
