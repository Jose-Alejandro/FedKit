import telemetry.models as models
import csv

# Generate csv for training sessions

header = [
    "sesion_id", 
    "ellapsed_time"
]


csv_lines = [header]

trainingSessions = models.TrainingSession.objects.all()
for session in trainingSessions:
    csv_lines.append(
        [
            session.id, 
            session.end_time - session.start_time
        ]
    )

with open('./training_sessions.csv', 'w', newline='') as csv_file:
    write = csv.writer(
        csv_file, 
        quoting=csv.QUOTE_ALL
    )
    write.writerows(csv_lines)

# Generate csv for Fit_ins
header = [
    "id", 
    "device_id", 
    "session_id", 
    "ellapsed_time"
]
csv_lines = [header]

fitInsTelemetryData = models.FitInsTelemetryData.objects.all()
for fit_in in fitInsTelemetryData:
    csv_lines.append(
        [
            fit_in.id,
            fit_in.device_id,
            fit_in.session_id.id,
            fit_in.end - fit_in.start
        ]
    )

with open('./fit_in_times.csv', 'w', newline='') as csv_file:
    write = csv.writer(
        csv_file, 
        quoting=csv.QUOTE_ALL
    )
    write.writerows(csv_lines)


# Generate csv for evaluate_ins
header = [
    "id",
    "device_id",
    "session_id", 
    "test_size" ,
    "ellapsed_time"
]

csv_lines = [header]

evaluateInsTelemetryData = models.EvaluateInsTelemetryData.objects.all()
for ev_in in evaluateInsTelemetryData:
    csv_lines.append(
        [
            ev_in.id, 
            ev_in.device_id + (1<<64),
            ev_in.session_id.id,
            ev_in.test_size,
            ev_in.end - ev_in.start
        ]
    )

with open('./ev_in_times.csv', 'w', newline='') as csv_file:
    write = csv.writer(
        csv_file, 
        quoting=csv.QUOTE_ALL
    )
    write.writerows(csv_lines)
