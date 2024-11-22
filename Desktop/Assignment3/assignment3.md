# Assignment3
### Github:https://github.com/XieWen2000/Distributed-System/tree/main/Desktop/Assignment3
### 1. Database Design:

![Database Design.png](pics/%E6%88%AA%E5%B1%8F2024-11-21%20%E4%B8%8B%E5%8D%888.51.26.png)
### Design Description 
1.` SkierData` Table:
* `skierId`: Primary key, identifies the skier.
* `season`: Represents the season in which the activity takes place.
* `activities`: Stores all activities for the skier as a 
JSON object. This allows for embedding multiple fields 
(such as `day`, `resortId`, `checkIn`, `lifts`, etc.) within one column.
![截屏2024-11-21 下午8.58.08.png](pics/%E6%88%AA%E5%B1%8F2024-11-21%20%E4%B8%8B%E5%8D%888.58.08.png)
2. `ResortActivity` Table:

`resortId`: Identifier for the resort.
`checkIn`: The day the skiers checked in, used as part of the primary key.
`uniqueSkiers`: Stores an array of unique skier IDs in JSON format, representing all skiers who visited the resort on that day.
![截屏2024-11-21 下午8.56.55.png](pics/%E6%88%AA%E5%B1%8F2024-11-21%20%E4%B8%8B%E5%8D%888.56.55.png)
### 2. Database Query:
* For skier N, how many days have they skied this season?

`  db.Skiers.aggregate([
  { $match: { skierId: N, season: seasonID } },
  { $unwind: "$activities" },
  { $group: { _id: "$skierId", uniqueDays: { $addToSet: "$activities.day" } } },
  { $project: { totalDays: { $size: "$uniqueDays" } } }
  ]);`
* For skier N, what are the vertical totals for each ski day?

`  db.Skiers.aggregate([
  { $match: { skierId: N } },
  { $unwind: "$activities" },
  { $project: {
  day: "$activities.day",
  vertical: { $sum: { $map: {
  input: "$activities.lifts",
  as: "lift",
  in: { $multiply: ["$$lift.liftId", 10] }
  } } }
  } }
  ]);
`
* For skier N, show me the lifts they rode on each ski day.

`db.Skiers.aggregate([
{ $match: { skierId: N } },
{ $unwind: "$activities" },
{ $project: {
day: "$activities.day",
lifts: "$activities.lifts"
} }
]);`
* How many unique skiers visited resort X on day N?

`db.ResortActivity.findOne({ resortId: X, checkIn: N }).uniqueSkiers.length;
`
### 3. Test Output
Bandwidth:
![bandwidth.png](pics/bandwidth.png)

Client deployed in EC2 waiting for requests:
![截屏2024-11-21 上午12.01.50.png](pics/%E6%88%AA%E5%B1%8F2024-11-21%20%E4%B8%8A%E5%8D%8812.01.50.png)

Client deployed in EC2 has stored all requests in Mangodb:
![截屏2024-11-21 上午2.20.50.png](pics/%E6%88%AA%E5%B1%8F2024-11-21%20%E4%B8%8A%E5%8D%882.20.50.png)

Consumer Channels in RabbitMq:
![截屏2024-11-20 下午11.48.07.png](pics/%E6%88%AA%E5%B1%8F2024-11-20%20%E4%B8%8B%E5%8D%8811.48.07.png)
Bandwidth of RabbitMq:
![截屏2024-11-20 下午11.49.55.png](pics/%E6%88%AA%E5%B1%8F2024-11-20%20%E4%B8%8B%E5%8D%8811.49.55.png)
![截屏2024-11-20 下午11.48.40.png](pics/%E6%88%AA%E5%B1%8F2024-11-20%20%E4%B8%8B%E5%8D%8811.48.40.png)