#!/usr/bin/env python3
"""
Generate sample TPC-DS data for testing.

This script generates minimal sample data for TPC-DS Q1.
For production use, use the official TPC-DS tools from tpc.org.
"""

import csv
import random
from datetime import datetime, timedelta

def generate_date_dim(filename, num_rows=100):
    """Generate sample date_dim data."""
    print(f"Generating {filename}...")
    
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f, delimiter='|')
        
        # Header
        writer.writerow(['d_date_sk', 'd_date_id', 'd_date', 'd_month_seq', 
                        'd_week_seq', 'd_quarter_seq', 'd_year', 'd_dow', 
                        'd_moy', 'd_dom', 'd_qoy'])
        
        start_date = datetime(2020, 1, 1)
        for i in range(num_rows):
            date = start_date + timedelta(days=i)
            writer.writerow([
                i + 1,  # d_date_sk
                date.strftime('%Y%m%d'),  # d_date_id
                date.strftime('%Y-%m-%d'),  # d_date
                (date.year - 2020) * 12 + date.month,  # d_month_seq
                i // 7 + 1,  # d_week_seq
                (date.year - 2020) * 4 + (date.month - 1) // 3 + 1,  # d_quarter_seq
                date.year,  # d_year
                date.weekday(),  # d_dow
                date.month,  # d_moy
                date.day,  # d_dom
                (date.month - 1) // 3 + 1  # d_qoy
            ])

def generate_store(filename):
    """Generate sample store data."""
    print(f"Generating {filename}...")
    
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f, delimiter='|')
        
        # Header
        writer.writerow(['s_store_sk', 's_store_id', 's_store_name', 's_number_employees'])
        
        stores = [
            (1, 'STORE001', 'Downtown Store', 50),
            (2, 'STORE002', 'Mall Store', 75),
            (3, 'STORE003', 'Suburban Store', 40),
        ]
        
        for store in stores:
            writer.writerow(store)

def generate_customer(filename, num_rows=50):
    """Generate sample customer data."""
    print(f"Generating {filename}...")
    
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f, delimiter='|')
        
        # Header
        writer.writerow(['c_customer_sk', 'c_customer_id', 'c_current_addr_sk', 
                        'c_first_name', 'c_last_name', 'c_birth_year'])
        
        first_names = ['John', 'Jane', 'Bob', 'Alice', 'Charlie', 'Diana']
        last_names = ['Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia']
        
        for i in range(num_rows):
            writer.writerow([
                i + 1,  # c_customer_sk
                f'CUST{i+1:06d}',  # c_customer_id
                i + 1,  # c_current_addr_sk
                random.choice(first_names),  # c_first_name
                random.choice(last_names),  # c_last_name
                random.randint(1950, 2000)  # c_birth_year
            ])

def generate_store_sales(filename, num_rows=200):
    """Generate sample store_sales data."""
    print(f"Generating {filename}...")
    
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f, delimiter='|')
        
        # Header
        writer.writerow(['ss_sold_date_sk', 'ss_sold_time_sk', 'ss_item_sk', 
                        'ss_customer_sk', 'ss_store_sk', 'ss_quantity', 
                        'ss_sales_price', 'ss_ext_sales_price'])
        
        for i in range(num_rows):
            quantity = random.randint(1, 10)
            price = round(random.uniform(10.0, 500.0), 2)
            writer.writerow([
                random.randint(1, 100),  # ss_sold_date_sk
                random.randint(1, 86400),  # ss_sold_time_sk
                random.randint(1, 100),  # ss_item_sk
                random.randint(1, 50),  # ss_customer_sk
                random.randint(1, 3),  # ss_store_sk
                quantity,  # ss_quantity
                price,  # ss_sales_price
                round(quantity * price, 2)  # ss_ext_sales_price
            ])

def generate_store_returns(filename, num_rows=20):
    """Generate sample store_returns data."""
    print(f"Generating {filename}...")
    
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f, delimiter='|')
        
        # Header
        writer.writerow(['sr_returned_date_sk', 'sr_return_time_sk', 'sr_item_sk', 
                        'sr_customer_sk', 'sr_store_sk', 'sr_return_quantity', 
                        'sr_return_amt'])
        
        for i in range(num_rows):
            quantity = random.randint(1, 5)
            amount = round(random.uniform(10.0, 200.0), 2)
            writer.writerow([
                random.randint(1, 100),  # sr_returned_date_sk
                random.randint(1, 86400),  # sr_return_time_sk
                random.randint(1, 100),  # sr_item_sk
                random.randint(1, 50),  # sr_customer_sk
                random.randint(1, 3),  # sr_store_sk
                quantity,  # sr_return_quantity
                amount  # sr_return_amt
            ])

def main():
    """Generate all sample data files."""
    print("Generating TPC-DS sample data...")
    print("=" * 50)
    
    generate_date_dim('date_dim.csv', 100)
    generate_store('store.csv')
    generate_customer('customer.csv', 50)
    generate_store_sales('store_sales.csv', 200)
    generate_store_returns('store_returns.csv', 20)
    
    print("=" * 50)
    print("Sample data generation complete!")
    print("\nNote: This is minimal sample data for testing.")
    print("For production use, generate data using official TPC-DS tools.")
    print("Download from: http://www.tpc.org/tpcds/")

if __name__ == '__main__':
    main()

# Made with Bob
