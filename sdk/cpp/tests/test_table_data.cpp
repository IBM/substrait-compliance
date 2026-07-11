#include <gtest/gtest.h>
#include <substrait_compliance/table_data.h>

using namespace substrait::compliance;

TEST(TableDataTest, Construction) {
    TableData table;
    EXPECT_TRUE(table.empty());
    EXPECT_EQ(table.row_count(), 0);
}

TEST(TableDataTest, AddRows) {
    TableData table;
    table.set_columns({{"id", "INTEGER"}, {"name", "STRING"}});
    
    table.add_row({1, std::string("Alice")});
    table.add_row({2, std::string("Bob")});
    
    EXPECT_EQ(table.row_count(), 2);
    EXPECT_EQ(table.column_count(), 2);
}

TEST(TableDataTest, GetCell) {
    TableData table;
    table.set_columns({{"value", "INTEGER"}});
    table.add_row({42});
    
    auto cell = table.get_cell(0, 0);
    EXPECT_TRUE(std::holds_alternative<int32_t>(cell));
    EXPECT_EQ(std::get<int32_t>(cell), 42);
}

TEST(CellValueTest, IsNull) {
    CellValue null_val = nullptr;
    CellValue int_val = 42;
    
    EXPECT_TRUE(cell_value::is_null(null_val));
    EXPECT_FALSE(cell_value::is_null(int_val));
}

