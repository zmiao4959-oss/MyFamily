from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str
    version: str
    database: str


class PairRequest(BaseModel):
    pairing_token: str = Field(min_length=8, max_length=256)
    device_name: str = Field(min_length=1, max_length=120)


class PairResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    server_version: str


class RevokeResponse(BaseModel):
    revoked: bool
