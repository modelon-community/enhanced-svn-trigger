
**This project is not actively developed**

Enhanced SVN Trigger
====================

The Enhanced SVN Trigger triggers new builds for monitored jobs when changes 
are made to SVN URLs used by the job. New builds are triggered for each change 
that is made to the SVN URLs used by the job. This behavior differs from the 
Jenkins default when it comes to polling, where multiple commits may be served 
by the same build.

Building
--------
On Linux:

```
make
```

On Windows:

```
make -f windows.mk

