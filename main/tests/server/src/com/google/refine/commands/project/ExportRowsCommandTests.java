
package com.google.refine.commands.project;

import java.io.IOException;

import javax.servlet.ServletException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.refine.commands.CommandTestBase;

public class ExportRowsCommandTests extends CommandTestBase {

    @BeforeMethod
    public void setUpCommand() {
        command = new ExportRowsCommand();
    }

    @Test
    public void testCSRFProtection() throws ServletException, IOException {
        command.doPost(request, response);
        assertCSRFCheckFailed();
    }
}
