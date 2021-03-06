terraform {
  backend "s3" {
    bucket = "terraform-state-kodemaker"
    key = "prod/kodemaker-www.tfstate"
    region = "eu-west-1"
    dynamodb_table = "terraform-locks"
  }
}

provider "aws" {
  version = "~> 2.0"
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::195221715009:role/Deployer"
  }
}

provider "aws" {
  version = "~> 2.0"
  region = "us-east-1"
  alias = "us-east-1"

  assume_role {
    role_arn = "arn:aws:iam::195221715009:role/Deployer"
  }
}
