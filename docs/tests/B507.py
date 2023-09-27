##Patterns: B507

from paramiko import client

ssh_client = client.SSHClient()
##Err: B507
ssh_client.set_missing_host_key_policy(client.AutoAddPolicy)
##Err: B507
ssh_client.set_missing_host_key_policy(client.WarningPolicy)
