output "ec2_instance_id" {
  description = "EC2 instance ID"
  value       = aws_instance.main.id
}

output "ec2_public_ip" {
  description = "EC2 public IP address"
  value       = aws_instance.main.public_ip
}

output "ec2_public_dns" {
  description = "EC2 public DNS"
  value       = aws_instance.main.public_dns
}

output "vpc_id" {
  description = "Created VPC ID"
  value       = aws_vpc.main.id
}

output "security_group_id" {
  description = "EC2 security group ID"
  value       = aws_security_group.main.id
}

output "user_ecr_repository_url" {
  value = aws_ecr_repository.user_service.repository_url
}

output "product_ecr_repository_url" {
  value = aws_ecr_repository.product_service.repository_url
}

output "order_ecr_repository_url" {
  value = aws_ecr_repository.order_service.repository_url
}