import logging

from rest_framework import permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.views import Request  # type: ignore
from telemetry.serializers import (
    EvaluateInsTelemetryDataSerializer,
    FitInsTelemetryDataSerializer,
    UpTimesTelemetryDataSerializer,
)

logger = logging.getLogger(__name__)


def save_serializer_and_respond(serializer) -> Response:
    if serializer.is_valid():
        serializer.save()
    else:
        logger.error(serializer.errors)
    return Response("")


@api_view(["POST"])
@permission_classes((permissions.AllowAny,))
def fit_ins(request: Request):
    # print("ALEX fit_ins endpoint")
    print("\nALEX telemetry/fit_ins endpoint request is: ", request.data)
    serializer = FitInsTelemetryDataSerializer(data=request.data)  # type: ignore
    serializer.is_valid()
    print("ALEX telemetry/fit_ins endpoint response is: ", serializer.validated_data)
    return save_serializer_and_respond(serializer)


@api_view(["POST"])
@permission_classes((permissions.AllowAny,))
def evaluate_ins(request: Request):
    # print("ALEX evaluate_ins endpoint")
    print("\nALEX telemetry/evaluate_ins endpoint request data is: ", request.data)
    serializer = EvaluateInsTelemetryDataSerializer(data=request.data)  # type: ignore
    serializer.is_valid()
    print("ALEX telemetry/evaluate_ins endpoint response data is: ", serializer.validated_data)
    return save_serializer_and_respond(serializer)


@api_view(["POST"])
@permission_classes((permissions.AllowAny,))
def up_times(request: Request):
    # print("ALEX get_time endpoint, this is a test, should not be called yet")
    # print("the data request is:", request.data)
    serializer = UpTimesTelemetryDataSerializer(data=request.data)  # type: ignore
    # serializer.is_valid()
    # print("ALEX the data response is", serializer.validated_data)
    return save_serializer_and_respond(serializer)
