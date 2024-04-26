from django.db import models
from train.models import MLModel


class TrainingSession(models.Model):
    id: int  # Help static analysis.
    tflite_model = models.ForeignKey(
        MLModel,
        on_delete=models.CASCADE,
        related_name="training_sessions",
        editable=False,
    )
    start_time = models.DateTimeField(auto_now_add=True)
    end_time = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"Training session {self.id} for {self.tflite_model} \
<{self.start_time} - {self.end_time}>"


# Always change together with Android `Train.FitInsTelemetryData`.
class FitInsTelemetryData(models.Model):
    id: int  # Help static analysis.
    device_id = models.BigIntegerField(editable=False)
    session_id = models.ForeignKey(
        TrainingSession,
        on_delete=models.CASCADE,
        related_name="fit_ins",
        editable=False,
    )
    start = models.DateTimeField(editable=False)
    end = models.DateTimeField(editable=False)

    def __str__(self) -> str:
        return f"FitIns {self.id} on {self.device_id} {self.start} - {self.end}"


# Always change together with Android `Train.EvaluateInsTelemetryData`.
class EvaluateInsTelemetryData(models.Model):
    id: int  # Help static analysis.
    device_id = models.BigIntegerField(editable=False)
    session_id = models.ForeignKey(
        TrainingSession,
        on_delete=models.CASCADE,
        related_name="evaluate_ins",
        editable=False,
    )
    start = models.DateTimeField(editable=False)
    end = models.DateTimeField(editable=False)
    loss = models.FloatField(editable=False)
    accuracy = models.FloatField(editable=False)
    test_size = models.BigIntegerField(editable=False)

    def __str__(self) -> str:
        return f"EvaluateInsTelemetryData {self.id} on {self.device_id} {self.start} - \
{self.end} loss: {self.loss} accuracy: {self.accuracy} test_size: {self.test_size}"

# Always change together with Android `Train.UpTimesDataTelemetry`.
class UpTimesData(models.Model):
    id: int  # Help static analysis.
    device_id = models.BigIntegerField(editable=False)
    session_id = models.ForeignKey(
        TrainingSession,
        on_delete=models.CASCADE,
        related_name="up_times",
        editable=False,
    )
    function_name = models.CharField(max_length=256,editable=False)
    elapsed_time = models.IntegerField(editable=False)

    def __str__(self) -> str:
        return f"UpTimesData {self.id} on device: {self.device_id} with session_id: {self.session_id.id}  \
function_name: {self.function_name} total_time: {self.elapsed_time}"
