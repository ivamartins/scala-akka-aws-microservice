variable "name" { type = string }

resource "aws_dynamodb_table" "orders" {
  name         = "${var.name}-orders"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"
  attribute {
    name = "id"
    type = "S"
  }
  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }
  tags = { Name = "${var.name}-orders" }
}

output "orders_table_name" { value = aws_dynamodb_table.orders.name }
output "orders_table_arn"  { value = aws_dynamodb_table.orders.arn }
