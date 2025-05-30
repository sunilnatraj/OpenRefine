/*******************************************************************************
 * Copyright (C) 2018, OpenRefine contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package com.google.refine.operations.recon;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.node.TextNode;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.refine.RefineTest;
import com.google.refine.model.AbstractOperation;
import com.google.refine.model.ColumnsDiff;
import com.google.refine.model.Project;
import com.google.refine.model.recon.StandardReconConfig;
import com.google.refine.operations.OperationDescription;
import com.google.refine.operations.OperationRegistry;
import com.google.refine.util.ParsingUtilities;
import com.google.refine.util.TestUtils;

public class ReconUseValuesAsIdsOperationTests extends RefineTest {

    String json = "{"
            + "\"op\":\"core/recon-use-values-as-identifiers\","
            + "\"description\":" + new TextNode(OperationDescription.recon_use_values_as_identifiers_brief("ids")).toString() + ","
            + "\"columnName\":\"ids\","
            + "\"engineConfig\":{\"mode\":\"row-based\",\"facets\":[]},"
            + "\"service\":\"http://localhost:8080/api\","
            + "\"identifierSpace\":\"http://test.org/entities/\","
            + "\"schemaSpace\":\"http://test.org/schema/\""
            + "}";

    @BeforeSuite
    public void registerOperation() {
        OperationRegistry.registerOperation(getCoreModule(), "recon-use-values-as-identifiers", ReconUseValuesAsIdentifiersOperation.class);
    }

    @Test
    public void serializeReconUseValuesAsIdentifiersOperation() throws Exception {
        TestUtils.isSerializedTo(ParsingUtilities.mapper.readValue(json, ReconUseValuesAsIdentifiersOperation.class), json);
    }

    @Test
    public void testColumnDependencies() throws Exception {
        AbstractOperation operation = ParsingUtilities.mapper.readValue(json, ReconUseValuesAsIdentifiersOperation.class);
        assertEquals(operation.getColumnsDiff(), Optional.of(ColumnsDiff.modifySingleColumn("ids")));
        assertEquals(operation.getColumnDependencies(), Optional.of(Set.of("ids")));
    }

    @Test
    public void testRename() throws Exception {
        ReconUseValuesAsIdentifiersOperation SUT = ParsingUtilities.mapper.readValue(json, ReconUseValuesAsIdentifiersOperation.class);

        ReconUseValuesAsIdentifiersOperation renamed = SUT.renameColumns(Map.of("ids", "identifiers"));

        String expectedJson = "{\n"
                + "       \"columnName\" : \"identifiers\",\n"
                + "       \"description\" : "
                + new TextNode(OperationDescription.recon_use_values_as_identifiers_brief("identifiers")).toString() + ",\n"
                + "       \"engineConfig\" : {\n"
                + "         \"facets\" : [ ],\n"
                + "         \"mode\" : \"row-based\"\n"
                + "       },\n"
                + "       \"identifierSpace\" : \"http://test.org/entities/\",\n"
                + "       \"op\" : \"core/recon-use-values-as-identifiers\",\n"
                + "       \"schemaSpace\" : \"http://test.org/schema/\",\n"
                + "       \"service\" : \"http://localhost:8080/api\"\n"
                + "     }";
        TestUtils.isSerializedTo(renamed, expectedJson);
    }

    @Test
    public void testUseValuesAsIds() throws Exception {
        Project project = createProject(
                new String[] { "ids", "v" },
                new Serializable[][] {
                        { "Q343", "hello" },
                        { null, "world" },
                        { "http://test.org/entities/Q31", "test" }
                });
        ReconUseValuesAsIdentifiersOperation op = ParsingUtilities.mapper.readValue(json, ReconUseValuesAsIdentifiersOperation.class);

        runOperation(op, project);

        assertEquals("Q343", project.rows.get(0).cells.get(0).recon.match.id);
        assertEquals("http://test.org/entities/", project.rows.get(0).cells.get(0).recon.identifierSpace);
        assertNull(project.rows.get(1).cells.get(0));
        assertEquals("Q31", project.rows.get(2).cells.get(0).recon.match.id);
        assertEquals(2, project.columnModel.columns.get(0).getReconStats().matchedTopics);
        assertEquals("http://test.org/schema/", ((StandardReconConfig) project.columnModel.columns.get(0).getReconConfig()).schemaSpace);
    }
}
