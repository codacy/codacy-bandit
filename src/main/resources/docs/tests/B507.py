##Patterns: B507

from paramiko import client

ssh_client = client.SSHClient()
##Warn: B507
ssh_client.set_missing_host_key_policy(client.AutoAddPolicy)
##Warn: B507
ssh_client.set_missing_host_key_policy(client.WarningPolicy)
