#include <gtest/gtest.h>
#include <substrait_compliance/comparator.h>

using namespace substrait::compliance;

TEST(ResultComparatorTest, CompareEqualTables) {
    TableData table1, table2;
    table1.set_columns({{"id", "INTEGER"}});
    table2.set_columns({{"id", "INTEGER"}});
    
    table1.add_row({1});
    table2.add_row({1});
    
    ResultComparator comparator;
    auto result = comparator.compare_tables(table1, table2);
    
    EXPECT_TRUE(result.matches);
}

TEST(ResultComparatorTest, CompareDifferentTables) {
    TableData table1, table2;
    table1.set_columns({{"id", "INTEGER"}});
    table2.set_columns({{"id", "INTEGER"}});
    
    table1.add_row({1});
    table2.add_row({2});
    
    ResultComparator comparator;
    auto result = comparator.compare_tables(table1, table2);
    
    EXPECT_FALSE(result.matches);
}

TEST(ResultComparatorTest, CompareFloatsWithEpsilon) {
    ResultComparator comparator;
    
    // 5e-10 is within the default epsilon of 1e-9
    CellValue a = 1.0;
    CellValue b = 1.0 + 5e-10;
    
    EXPECT_TRUE(comparator.compare_values(a, b, "DOUBLE"));
}

TEST(TypeAwareComparatorTest, IsNumericType) {
    EXPECT_TRUE(TypeAwareComparator::is_numeric_type("INTEGER"));
    EXPECT_TRUE(TypeAwareComparator::is_numeric_type("DOUBLE"));
    EXPECT_FALSE(TypeAwareComparator::is_numeric_type("STRING"));
}

