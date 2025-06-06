
package com.google.refine.operations;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.testng.annotations.Test;

import com.google.refine.model.AbstractOperation;
import com.google.refine.model.ColumnsDiff;
import com.google.refine.util.ParsingUtilities;
import com.google.refine.util.TestUtils;

public class RecipeTests {

    String json = "[{\"op\":\"unknown\",\"description\":\"some operation\"}]";

    //// Sample test operations

    /**
     * An operation which removes a column (faithful to the actual such operation in OpenRefine, which isn't visible in
     * this module).
     */
    static class ColumnRemovalOperation extends AbstractOperation {

        final String columnName;

        public ColumnRemovalOperation(String columnName) {
            this.columnName = columnName;
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.of(Set.of(columnName));
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.of(ColumnsDiff.builder().deleteColumn(columnName).build());
        }
    }

    /**
     * An operation which renames a column (also faithful to the actual such operation in OpenRefine, which isn't
     * visible in this module).
     */
    static class ColumnRenameOperation extends AbstractOperation {

        final String oldName;
        final String newName;

        public ColumnRenameOperation(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.of(Set.of(oldName));
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.of(ColumnsDiff.builder().deleteColumn(oldName).addColumn(newName, oldName).build());
        }

        @Override
        public AbstractOperation renameColumns(Map<String, String> newColumnNames) {
            return new ColumnRenameOperation(
                    newColumnNames.getOrDefault(oldName, oldName),
                    newColumnNames.getOrDefault(newName, newName));
        }

        @Override
        public int hashCode() {
            return Objects.hash(newName, oldName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ColumnRenameOperation other = (ColumnRenameOperation) obj;
            return Objects.equals(newName, other.newName) && Objects.equals(oldName, other.oldName);
        }

    }

    /**
     * An operation which exposes its dependencies, but not its impact on columns after having run (just like the
     * ColumnSplitOperation in OpenRefine, not visible here)
     */
    static class ColumnSplitOperation extends AbstractOperation {

        final String columnName;

        public ColumnSplitOperation(String columnName) {
            this.columnName = columnName;
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.of(Set.of(columnName));
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.empty();
        }
    }

    /**
     * An operation which modifies a single column, like the transform operation in OpenRefine (not visible here).
     */
    class ColumnTransformOperation extends AbstractOperation {

        final String columnName;

        public ColumnTransformOperation(String columnName) {
            this.columnName = columnName;
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.of(Set.of(columnName));
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.of(ColumnsDiff.modifySingleColumn(columnName));
        }
    }

    /**
     * An operation which declares neither the columns it depends on, nor its impact on the columns after having run.
     */
    static class OpaqueOperation extends AbstractOperation {

        OpaqueOperation() {
        }

        @Override
        public Optional<Set<String>> getColumnDependencies() {
            return Optional.empty();
        }

        @Override
        public Optional<ColumnsDiff> getColumnsDiff() {
            return Optional.empty();
        }
    }

    @Test
    public void testDeserialize() throws Exception {
        Recipe recipe = ParsingUtilities.mapper.readValue(json, Recipe.class);

        assertEquals(recipe.getOperations().size(), 1);
        assertEquals(recipe.getOperations().get(0).getOperationId(), "unknown");

        TestUtils.isSerializedTo(recipe, json);
    }

    @Test
    public void testValidateMethod() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(List.of(
                new UnknownOperation("some-operation", "Some description"))).validate());

        assertThrows(IllegalArgumentException.class, () -> new Recipe(Collections.singletonList(null)).validate());

        new Recipe(List.of(
                new ColumnRemovalOperation("foo"))).validate();
    }

    @Test
    public void testGetRequiredColumns() throws Exception {
        assertEquals(
                new Recipe(List.of()).getRequiredColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRemovalOperation("foo"))).getRequiredColumns(),
                Set.of("foo"));

        assertEquals(
                new Recipe(List.of(
                        new ColumnRemovalOperation("foo"),
                        new ColumnRemovalOperation("bar"))).getRequiredColumns(),
                Set.of("foo", "bar"));

        assertEquals(
                new Recipe(List.of(
                        new ColumnRenameOperation("foo", "foo2"),
                        new ColumnRemovalOperation("bar"))).getRequiredColumns(),
                Set.of("foo", "bar"));

        assertEquals(
                new Recipe(List.of(
                        new ColumnRenameOperation("foo", "foo2"),
                        new ColumnSplitOperation("foo2"),
                        // The dependency of the following operation is not taken into account,
                        // because the previous operation does not expose a columns diff,
                        // so we can't predict if "bar" is going to be produced by it or not.
                        new ColumnRemovalOperation("bar"))).getRequiredColumns(),
                Set.of("foo"));

        assertEquals(
                new Recipe(List.of(
                        new ColumnTransformOperation("foo"),
                        new ColumnRemovalOperation("foo"))).getRequiredColumns(),
                Set.of("foo"));

        // unanalyzable operation
        assertEquals(
                new Recipe(List.of(
                        new OpaqueOperation())).getRequiredColumns(),
                Set.of());
    }

    @Test
    public void testGetNewColumns() throws Exception {
        assertEquals(
                new Recipe(List.of()).getNewColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRemovalOperation("foo"))).getNewColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRenameOperation("foo", "foo2"),
                        new ColumnRemovalOperation("bar"))).getNewColumns(),
                Set.of("foo2"));

        // unanalyzable operation
        assertEquals(
                new Recipe(List.of(
                        new OpaqueOperation())).getNewColumns(),
                Set.of());
    }

    @Test
    public void testGetInternalColumns() throws Exception {
        assertEquals(
                new Recipe(List.of()).getInternalColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRemovalOperation("foo"))).getInternalColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRemovalOperation("foo"),
                        new ColumnRemovalOperation("bar"))).getInternalColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRenameOperation("foo", "foo2"),
                        new ColumnRemovalOperation("bar"))).getInternalColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRenameOperation("foo", "foo2"),
                        new ColumnSplitOperation("foo2"), // opaque
                        new ColumnRemovalOperation("bar"))).getInternalColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnTransformOperation("foo"),
                        new ColumnRemovalOperation("foo"))).getInternalColumns(),
                Set.of());

        // unanalyzable operation
        assertEquals(
                new Recipe(List.of(
                        new OpaqueOperation())).getInternalColumns(),
                Set.of());

        assertEquals(
                new Recipe(List.of(
                        new ColumnRenameOperation("foo", "foo2"),
                        new ColumnRenameOperation("foo2", "foo3"))).getInternalColumns(),
                Set.of("foo2"));
    }

    @Test
    public void testRequiredColumnsFromInconsistentOperations() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(List.of(
                new ColumnRemovalOperation("foo"),
                new ColumnRenameOperation("foo", "bar"))).getRequiredColumns());
    }

    @Test
    public void testConflictingColumnCreation() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(List.of(
                new ColumnTransformOperation("bar"),
                new ColumnRenameOperation("foo", "bar"))).getRequiredColumns());
    }

    @Test
    public void testInternalColumnRenaming() {
        Recipe recipe = new Recipe(List.of(
                new ColumnRenameOperation("foo", "foo2"),
                new ColumnRenameOperation("foo2", "foo3")));

        Recipe rewritten = recipe.avoidInternalColumnCollisions(Set.of("foo", "foo2"));

        assertEquals(rewritten, new Recipe(List.of(
                new ColumnRenameOperation("foo", "foo2_2"),
                new ColumnRenameOperation("foo2_2", "foo3"))));
    }

}
