##Patterns: B615

from transformers import AutoModel

##Warn: B615
model = AutoModel.from_pretrained("org/model_name")

# Safe usage: pinned to an immutable commit revision
safe_model = AutoModel.from_pretrained(
    "org/model_name", revision="5d0f2e8a7f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d"
)
