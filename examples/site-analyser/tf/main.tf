resource "aws_lambda_function_url" "lambda" {
  function_name = aws_lambda_function.lambda.function_name
  authorization_type = "NONE"
}

output "function_url" {
  value = aws_lambda_function_url.lambda.function_url
}

resource "aws_dynamodb_table" "site_analyser" {
  name = "site-analyser"
  billing_mode = "PAY_PER_REQUEST"
  hash_key = "url"
  range_key = "date"

  attribute {
    name = "url"
    type = "S"
  }

  attribute {
    name = "date"
    type = "S"
  }
}

resource "aws_iam_role" "lambda" {
  name = "site-analyser-example-lambda"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_policy" "lambda" {
  name = "site-analyser-example-lambda"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.lambda.arn}:*"
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:QueryItem",
          "dynamodb:Scan",
          "dynamodb:UpdateItem",
        ]
        Resource = aws_dynamodb_table.site_analyser.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda" {
  role = aws_iam_role.lambda.name
  policy_arn = aws_iam_policy.lambda.arn
}
