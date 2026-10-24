# Timely

A personal focus tracker. Time your work against the things you actually spend your time on, like coding or guitar, and see how much each one got every day.

## What it does

- **Track time on your own activities.** Create activities, give them a colour, and start a stopwatch whenever you begin working on one. Switch activities and the time is split cleanly between them.
- **Stopwatch or countdown.** Run open-ended, or set a length and let it end on its own.
- **Pomodoro.** Repeating cycles of focus and rest. The breaks start themselves when a focus block ends, and the next block waits until you're ready.
- **Breaks are tracked, not counted.** Rest time is recorded but never inflates your focus time.
- **Add time after the fact.** Forgot to start the timer? Log the session afterwards, as long as it doesn't overlap another one.
- **Daily totals.** See focus time and number of sessions per activity for each day, plus how long you rested.
- **Yours alone.** Nobody else sees your data.

## Status

Early days. The groundwork is in place and the first working version is being built. Apps for the web, phone, command line and desktop are planned.

## Running it locally

You'll need Java 25 and Docker installed.

1. Start the database: `docker compose up -d`
2. From `apps/server`, start the server: `./mvnw spring-boot:run`. Flyway creates the tables on startup.

The server listens on port 3000. Override the database with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.
