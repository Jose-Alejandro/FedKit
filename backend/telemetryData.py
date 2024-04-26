import telemetry.models as models



trainingSessions = models.UpTimesData.objects.all()

for v in trainingSessions:
    print(v, "\n")
