# Breaks are a kind of session, not an activity

A break could have been modelled as a "Break" activity each user owns. We made it a kind of session instead: a break session has no activity and never counts as focus time. A Break activity could be renamed, archived or duplicated by the user, would leak into every focus total unless special-cased everywhere, and would lose the link to the work it interrupted. As a session kind, a break is still tracked, sits inside a pomodoro cycle next to the focus sessions it separates, and gets its own daily total.
