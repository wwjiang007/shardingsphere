/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.binder.context.segment.table;

import org.apache.shardingsphere.infra.database.core.type.DatabaseType;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereColumn;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereSchema;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereTable;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.column.ColumnSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.AliasSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.OwnerSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.table.SimpleTableSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.table.TableNameSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.table.TableSegment;
import org.apache.shardingsphere.sql.parser.statement.core.value.identifier.IdentifierValue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TablesContextTest {
    
    private final DatabaseType databaseType = TypedSPILoader.getService(DatabaseType.class, "FIXTURE");
    
    @Test
    void assertGetTableNamesWithoutTableSegments() {
        assertTrue(new TablesContext(Collections.emptyList(), Collections.emptyMap(), databaseType, "foo_db").getTableNames().isEmpty());
    }
    
    @Test
    void assertGetTableNamesWithoutSimpleTableSegments() {
        assertTrue(new TablesContext(Collections.singleton(mock(TableSegment.class)), Collections.emptyMap(), databaseType, "foo_db").getTableNames().isEmpty());
    }
    
    @Test
    void assertGetTableNames() {
        TablesContext tablesContext = new TablesContext(
                Arrays.asList(createTableSegment("table_1", "tbl_1"), createTableSegment("table_2", "tbl_2"), createTableSegment("DUAL", "dual")), databaseType, "foo_db");
        assertThat(tablesContext.getTableNames(), is(new HashSet<>(Arrays.asList("table_1", "table_2"))));
    }
    
    @Test
    void assertInstanceCreatedWhenNoExceptionThrown() {
        SimpleTableSegment tableSegment = new SimpleTableSegment(new TableNameSegment(0, 10, new IdentifierValue("tbl")));
        tableSegment.setOwner(new OwnerSegment(0, 0, new IdentifierValue("schema")));
        TablesContext tablesContext = new TablesContext(Collections.singleton(tableSegment), databaseType, "foo_db");
        assertThat(tablesContext.getDatabaseName(), is(Optional.of("schema")));
        assertThat(tablesContext.getSchemaName(), is(Optional.of("schema")));
        assertThat(tablesContext.getTableNames(), is(Collections.singleton("tbl")));
    }
    
    @Test
    void assertFindTableNameWhenSingleTable() {
        SimpleTableSegment tableSegment = createTableSegment("table_1", "tbl_1");
        ColumnSegment columnSegment = createColumnSegment(null, "col");
        Map<String, String> actual = new TablesContext(Collections.singletonList(tableSegment), databaseType, "foo_db").findTableNames(Collections.singletonList(columnSegment), mock());
        assertFalse(actual.isEmpty());
        assertThat(actual.get("col"), is("table_1"));
    }
    
    @Test
    void assertFindTableNameWhenColumnSegmentOwnerPresent() {
        SimpleTableSegment tableSegment1 = createTableSegment("table_1", "tbl_1");
        SimpleTableSegment tableSegment2 = createTableSegment("table_2", "tbl_2");
        ColumnSegment columnSegment = createColumnSegment("table_1", "col");
        Map<String, String> actual = new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db").findTableNames(Collections.singletonList(columnSegment), mock());
        assertFalse(actual.isEmpty());
        assertThat(actual.get("table_1.col"), is("table_1"));
    }
    
    @Test
    void assertFindTableNameWhenColumnSegmentOwnerAbsent() {
        SimpleTableSegment tableSegment1 = createTableSegment("table_1", "tbl_1");
        SimpleTableSegment tableSegment2 = createTableSegment("table_2", "tbl_2");
        ColumnSegment columnSegment = createColumnSegment(null, "col");
        Map<String, String> actual = new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db").findTableNames(Collections.singletonList(columnSegment), mock());
        assertTrue(actual.isEmpty());
    }
    
    @Test
    void assertFindTableNameWhenColumnSegmentOwnerAbsentAndSchemaMetaDataContainsColumn() {
        SimpleTableSegment tableSegment1 = createTableSegment("table_1", "tbl_1");
        SimpleTableSegment tableSegment2 = createTableSegment("table_2", "tbl_2");
        ShardingSphereSchema schema = mock(ShardingSphereSchema.class);
        when(schema.containsTable("table_1")).thenReturn(true);
        ShardingSphereTable table = mock(ShardingSphereTable.class);
        when(table.containsColumn("col")).thenReturn(true);
        when(schema.getTable("table_1")).thenReturn(table);
        ColumnSegment columnSegment = createColumnSegment(null, "col");
        Map<String, String> actual = new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db").findTableNames(Collections.singletonList(columnSegment), schema);
        assertThat(actual.get("col"), is("table_1"));
    }
    
    @Test
    void assertFindTableNameWhenColumnSegmentOwnerAbsentAndSchemaMetaDataContainsColumnInUpperCase() {
        SimpleTableSegment tableSegment1 = createTableSegment("TABLE_1", "TBL_1");
        SimpleTableSegment tableSegment2 = createTableSegment("TABLE_2", "TBL_2");
        ShardingSphereTable table = new ShardingSphereTable("TABLE_1",
                Collections.singletonList(new ShardingSphereColumn("COL", 0, false, false, true, true, false, false)), Collections.emptyList(), Collections.emptyList());
        ShardingSphereSchema schema = new ShardingSphereSchema("foo_db", Collections.singleton(table), Collections.emptyList());
        ColumnSegment columnSegment = createColumnSegment(null, "COL");
        Map<String, String> actual = new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db").findTableNames(Collections.singletonList(columnSegment), schema);
        assertFalse(actual.isEmpty());
        assertThat(actual.get("col"), is("TABLE_1"));
    }
    
    private SimpleTableSegment createTableSegment(final String tableName, final String alias) {
        SimpleTableSegment result = new SimpleTableSegment(new TableNameSegment(0, 0, new IdentifierValue(tableName)));
        AliasSegment aliasSegment = new AliasSegment(0, 0, new IdentifierValue(alias));
        result.setAlias(aliasSegment);
        return result;
    }
    
    private ColumnSegment createColumnSegment(final String owner, final String name) {
        ColumnSegment result = new ColumnSegment(0, 0, new IdentifierValue(name));
        if (null != owner) {
            result.setOwner(new OwnerSegment(0, 0, new IdentifierValue(owner)));
        }
        return result;
    }
    
    @Test
    void assertGetSchemaNameWithSameSchemaAndSameTable() {
        SimpleTableSegment tableSegment1 = createTableSegment("table_1", "tbl_1");
        tableSegment1.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_1")));
        SimpleTableSegment tableSegment2 = createTableSegment("table_1", "tbl_1");
        tableSegment2.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_1")));
        TablesContext tablesContext = new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db");
        assertTrue(tablesContext.getDatabaseName().isPresent());
        assertThat(tablesContext.getDatabaseName().get(), is("sharding_db_1"));
    }
    
    @Test
    void assertGetSchemaNameWithSameSchemaAndDifferentTable() {
        SimpleTableSegment tableSegment1 = createTableSegment("table_1", "tbl_1");
        tableSegment1.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_1")));
        SimpleTableSegment tableSegment2 = createTableSegment("table_2", "tbl_2");
        tableSegment2.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_1")));
        TablesContext tablesContext = new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db");
        assertTrue(tablesContext.getDatabaseName().isPresent());
        assertThat(tablesContext.getDatabaseName().get(), is("sharding_db_1"));
    }
    
    @Test
    void assertGetSchemaNameWithDifferentSchemaAndSameTable() {
        SimpleTableSegment tableSegment1 = createTableSegment("table_1", "tbl_1");
        tableSegment1.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_1")));
        SimpleTableSegment tableSegment2 = createTableSegment("table_1", "tbl_1");
        tableSegment2.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_2")));
        assertThrows(IllegalStateException.class, () -> new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db").getDatabaseName());
    }
    
    @Test
    void assertGetSchemaNameWithDifferentSchemaAndDifferentTable() {
        SimpleTableSegment tableSegment1 = createTableSegment("table_1", "tbl_1");
        tableSegment1.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_1")));
        SimpleTableSegment tableSegment2 = createTableSegment("table_2", "tbl_2");
        tableSegment2.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_2")));
        assertThrows(IllegalStateException.class, () -> new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db").getDatabaseName());
    }
    
    @Test
    void assertGetSchemaName() {
        SimpleTableSegment tableSegment1 = createTableSegment("table_1", "tbl_1");
        tableSegment1.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_1")));
        SimpleTableSegment tableSegment2 = createTableSegment("table_2", "tbl_2");
        tableSegment2.setOwner(new OwnerSegment(0, 0, new IdentifierValue("sharding_db_1")));
        TablesContext tablesContext = new TablesContext(Arrays.asList(tableSegment1, tableSegment2), databaseType, "foo_db");
        assertTrue(tablesContext.getSchemaName().isPresent());
        assertThat(tablesContext.getSchemaName().get(), is("sharding_db_1"));
    }
}
