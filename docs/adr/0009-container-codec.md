# ADR 0009: Container codec

Status: Partially verified; complex live items pending

Format v1 is one embedded version byte plus Paper item-array bytes. The database
codec column must match the embedded version and the decoded length must exactly
match logical size. Any failure aborts without padding, truncation or overwrite.
