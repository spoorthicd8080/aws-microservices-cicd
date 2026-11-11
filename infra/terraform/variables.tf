variable "aws_region" {
  description = "AWS region for project infrastructure"
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Project resource naming prefix"
  type        = string
  default     = "ecommerce-devops"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.small"
}

variable "key_pair_name" {
  description = "Existing AWS EC2 key pair name"
  type        = string
}

variable "admin_cidr" {
  description = "Your public IP address in CIDR notation"
  type        = string

  validation {
    condition     = can(cidrhost(var.admin_cidr, 0))
    error_message = "Enter a valid CIDR range, for example 203.0.113.10/32."
  }
}