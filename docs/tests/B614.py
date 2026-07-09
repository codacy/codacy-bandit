##Patterns: B614

import torch

##Warn: B614
loaded_weights = torch.load('model_weights.pth')

##Warn: B614
unsafe_weights = torch.load('model_weights.pth', weights_only=False)

# Safe usage should not be reported
safe_weights = torch.load('model_weights.pth', weights_only=True)
