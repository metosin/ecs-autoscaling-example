# Autoscaling: target tracking vs step scaling for scale-in

## How target tracking works

The autoscaling policy uses a custom metric **BacklogPerWorker**:

```
BacklogPerWorker = RedisQueueLength / RunningTaskCount
```

With a target value of 5, Application Auto Scaling adjusts the desired task count to
keep roughly 5 queue items per worker. When BacklogPerWorker rises above 5, tasks are
added; when it drops below 5, tasks are removed.

## Hidden alarms created by target tracking

AWS does not scale directly from your metric. Behind the scenes it creates two
CloudWatch alarms that you can see in the console but cannot modify:

| Alarm | Condition | Datapoints | Effect |
|---|---|---|---|
| Scale-out (high) | BacklogPerWorker > target | 3 of 3 × 1 min | Add tasks |
| Scale-in (low) | BacklogPerWorker < target | 15 of 15 × 1 min | Remove tasks |

The scale-in alarm is deliberately conservative: the metric must stay below target for
**15 consecutive 1-minute periods** (~15 minutes) before any scale-in action fires.
This is hardcoded by AWS and not configurable.

## Why scale-in feels slow

After a burst of work finishes, the queue drains and BacklogPerWorker drops to 0.
You might expect tasks to be removed quickly, but:

1. The hidden scale-in alarm waits 15 minutes of sustained low metric.
2. Only then does Auto Scaling remove some tasks.
3. `scale_in_cooldown` (set to 60 s in our config) governs the pause *between
   consecutive scale-in actions* — it does **not** shorten the initial 15-minute
   detection window.
4. So a full scale-down from many tasks to `min_capacity` can take 15+ minutes
   even when the queue has been empty the entire time.

## Step scaling approach

A step scaling policy paired with an explicit CloudWatch alarm gives full control over
evaluation periods.

### How it works

1. **Disable scale-in on the target tracking policy** (`disable_scale_in = true`).
   Target tracking still handles scale-out.
2. **Create a CloudWatch alarm** that fires when `BacklogPerWorker < target` for
   1 of 1 × 1-minute evaluation period.
3. **Create a step scaling policy** that reacts to the alarm by removing 1 task
   (ChangeInCapacity: -1). With a 60-second cooldown this removes one task per minute
   while the alarm is in ALARM state.

### Configuration

Set the Terraform variable:

```hcl
scale_in_policy = "step"   # default is "target_tracking"
```

### Trade-offs

| | Target tracking scale-in | Step scaling scale-in |
|---|---|---|
| Detection delay | ~15 min (hardcoded) | ~1 min (configurable) |
| Aggressiveness | Very conservative | More aggressive |
| Risk of flapping | Low | Higher if metric oscillates near threshold |
| Configuration | Zero — AWS manages it | You own the alarm and policy |
| Scale-out | Handled by target tracking | Handled by target tracking (unchanged) |

Step scaling is more aggressive, which means faster scale-in but a higher risk of
removing tasks too early if the metric bounces around the target value. For bursty
workloads where idle workers are costly, the faster response is usually worth it.
