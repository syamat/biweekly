package biweekly.io.scribe.property;

import static biweekly.ICalDataType.DATE;
import static biweekly.ICalDataType.DATE_TIME;
import static biweekly.ICalDataType.PERIOD;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import biweekly.ICalDataType;
import biweekly.ICalVersion;
import biweekly.io.ParseContext;
import biweekly.io.TimezoneInfo;
import biweekly.io.WriteContext;
import biweekly.io.json.JCalValue;
import biweekly.io.xml.XCalElement;
import biweekly.parameter.ICalParameters;
import biweekly.property.RecurrenceDates;
import biweekly.util.DateTimeComponents;
import biweekly.util.Duration;
import biweekly.util.ICalDate;
import biweekly.util.ICalDateFormat;
import biweekly.util.Period;

/*
 Copyright (c) 2013-2014, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Marshals {@link RecurrenceDates} properties.
 * @author Michael Angstadt
 */
public class RecurrenceDatesScribe extends ICalPropertyScribe<RecurrenceDates> {
	public RecurrenceDatesScribe() {
		super(RecurrenceDates.class, "RDATE", DATE_TIME);
	}

	@Override
	protected ICalParameters _prepareParameters(RecurrenceDates property, WriteContext context) {
		if (isInObservance(context)) {
			return property.getParameters();
		}

		List<Period> periods = property.getPeriods();
		List<ICalDate> dates = property.getDates();
		boolean hasTime;
		if (periods.isEmpty() && dates.isEmpty()) {
			hasTime = false;
		} else {
			ICalDataType dataType = dataType(property, context.getVersion());
			hasTime = (dataType == DATE_TIME || dataType == PERIOD);
		}
		return handleTzidParameter(property, hasTime, context);
	}

	@Override
	protected ICalDataType _dataType(RecurrenceDates property, ICalVersion version) {
		List<ICalDate> dates = property.getDates();
		if (!dates.isEmpty()) {
			return dates.get(0).hasTime() ? DATE_TIME : DATE;
		}

		if (!property.getPeriods().isEmpty()) {
			return PERIOD;
		}

		return defaultDataType(version);
	}

	@Override
	protected String _writeText(final RecurrenceDates property, WriteContext context) {
		final TimezoneInfo tzinfo = context.getTimezoneInfo();
		List<ICalDate> dates = property.getDates();
		if (!dates.isEmpty()) {
			final boolean inObservance = isInObservance(context);
			return list(dates, new ListCallback<ICalDate>() {
				public String asString(ICalDate date) {
					if (inObservance) {
						DateTimeComponents components = date.getRawComponents();
						if (components == null) {
							return date(date).time(true).floating(true).extended(false).write();
						}
						return components.toString(true, false);
					}
					return date(date).time(date.hasTime()).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).write();
				}
			});
		}

		//TODO vCal does not support periods
		List<Period> periods = property.getPeriods();
		if (!periods.isEmpty()) {
			return list(periods, new ListCallback<Period>() {
				public String asString(Period period) {
					StringBuilder sb = new StringBuilder();

					Date start = period.getStartDate();
					if (start != null) {
						String date = date(start).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).write();
						sb.append(date);
					}

					sb.append('/');

					Date end = period.getEndDate();
					Duration duration = period.getDuration();
					if (end != null) {
						String date = date(end).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).write();
						sb.append(date);
					} else if (duration != null) {
						sb.append(duration);
					}

					return sb.toString();
				}
			});
		}

		return "";
	}

	@Override
	protected RecurrenceDates _parseText(String value, ICalDataType dataType, ICalParameters parameters, ParseContext context) {
		return parse(list(value), dataType, parameters, context);
	}

	@Override
	protected void _writeXml(RecurrenceDates property, XCalElement element, WriteContext context) {
		TimezoneInfo tzinfo = context.getTimezoneInfo();
		ICalDataType dataType = dataType(property, context.getVersion());
		List<ICalDate> dates = property.getDates();
		if (!dates.isEmpty()) {
			boolean inObservance = isInObservance(context);
			for (ICalDate date : dates) {
				//@formatter:off
				String dateStr = inObservance ?
				date(date).time(true).floating(true).extended(true).write() :
				date(date).time(date.hasTime()).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).extended(true).write();
				//@formatter:on

				element.append(dataType, dateStr);
			}
			return;
		}

		List<Period> periods = property.getPeriods();
		if (!periods.isEmpty()) {
			for (Period period : periods) {
				XCalElement periodElement = element.append(dataType);

				Date start = period.getStartDate();
				if (start != null) {
					periodElement.append("start", date(start).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).extended(true).write());
				}

				Date end = period.getEndDate();
				if (end != null) {
					periodElement.append("end", date(end).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).extended(true).write());
				}

				Duration duration = period.getDuration();
				if (duration != null) {
					periodElement.append("duration", duration.toString());
				}
			}
			return;
		}

		element.append(defaultDataType, "");
	}

	@Override
	protected RecurrenceDates _parseXml(XCalElement element, ICalParameters parameters, ParseContext context) {
		List<XCalElement> periodElements = element.children(PERIOD);
		List<String> dateTimeElements = element.all(DATE_TIME);
		List<String> dateElements = element.all(DATE);
		if (periodElements.isEmpty() && dateTimeElements.isEmpty() && dateElements.isEmpty()) {
			throw missingXmlElements(PERIOD, DATE_TIME, DATE);
		}

		String tzid = parameters.getTimezoneId();
		RecurrenceDates property = new RecurrenceDates();

		//parse periods
		for (XCalElement periodElement : periodElements) {
			String startStr = periodElement.first("start");
			if (startStr == null) {
				context.addWarning(9);
				continue;
			}

			Date start;
			try {
				start = ICalDateFormat.parse(startStr);
			} catch (IllegalArgumentException e) {
				context.addWarning(10, startStr);
				continue;
			}

			String endStr = periodElement.first("end");
			if (endStr != null) {
				try {
					Date end = ICalDateFormat.parse(endStr);
					property.addPeriod(new Period(start, end));

					if (!ICalDateFormat.isUTC(startStr)) {
						if (tzid == null) {
							context.addFloatingDate(property, start, startStr);
						} else {
							context.addTimezonedDate(tzid, property, start, startStr);
						}
					}

					if (!ICalDateFormat.isUTC(endStr)) {
						if (tzid == null) {
							context.addFloatingDate(property, end, endStr);
						} else {
							context.addTimezonedDate(tzid, property, end, endStr);
						}
					}
				} catch (IllegalArgumentException e) {
					context.addWarning(11, endStr);
				}
				continue;
			}

			String durationStr = periodElement.first("duration");
			if (durationStr != null) {
				try {
					Duration duration = Duration.parse(durationStr);
					property.addPeriod(new Period(start, duration));

					if (!ICalDateFormat.isUTC(startStr)) {
						if (tzid == null) {
							context.addFloatingDate(property, start, startStr);
						} else {
							context.addTimezonedDate(tzid, property, start, startStr);
						}
					}
				} catch (IllegalArgumentException e) {
					context.addWarning(12, durationStr);
				}
				continue;
			}

			context.addWarning(13);
		}

		//parse date-times
		for (String dateTimeStr : dateTimeElements) {
			try {
				Date date = ICalDateFormat.parse(dateTimeStr);
				DateTimeComponents components = DateTimeComponents.parse(dateTimeStr);
				ICalDate icalDate = new ICalDate(date, components, true);
				property.addDate(icalDate);

				if (!ICalDateFormat.isUTC(dateTimeStr)) {
					if (tzid == null) {
						context.addFloatingDate(property, icalDate, dateTimeStr);
					} else {
						context.addTimezonedDate(tzid, property, icalDate, dateTimeStr);
					}
				}
			} catch (IllegalArgumentException e) {
				context.addWarning(15, dateTimeStr);
			}
		}

		//parse dates
		for (String dateStr : element.all(DATE)) {
			try {
				Date date = ICalDateFormat.parse(dateStr);
				DateTimeComponents components = DateTimeComponents.parse(dateStr);
				ICalDate icalDate = new ICalDate(date, components, false);
				property.addDate(icalDate);
			} catch (IllegalArgumentException e) {
				context.addWarning(15, dateStr);
			}
		}

		return property;
	}

	@Override
	protected JCalValue _writeJson(RecurrenceDates property, WriteContext context) {
		TimezoneInfo tzinfo = context.getTimezoneInfo();
		List<String> values = new ArrayList<String>();
		List<ICalDate> dates = property.getDates();
		List<Period> periods = property.getPeriods();
		if (!dates.isEmpty()) {
			boolean inObservance = isInObservance(context);
			for (ICalDate date : dates) {
				//@formatter:off
				String dateStr = inObservance ?
				date(date).time(true).floating(true).extended(true).write() :
				date(date).time(date.hasTime()).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).extended(true).write();
				//@formatter:on

				values.add(dateStr);
			}
		} else if (!periods.isEmpty()) {
			for (Period period : property.getPeriods()) {
				StringBuilder sb = new StringBuilder();
				if (period.getStartDate() != null) {
					String value = date(period.getStartDate()).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).extended(true).write();
					sb.append(value);
				}

				sb.append('/');

				Date end = period.getEndDate();
				Duration duration = period.getDuration();
				if (end != null) {
					String value = date(end).tz(tzinfo.isFloating(property), tzinfo.getTimeZoneToWriteIn(property)).extended(true).write();
					sb.append(value);
				} else if (duration != null) {
					sb.append(duration);
				}

				values.add(sb.toString());
			}
		}

		if (values.isEmpty()) {
			values.add("");
		}
		return JCalValue.multi(values);
	}

	@Override
	protected RecurrenceDates _parseJson(JCalValue value, ICalDataType dataType, ICalParameters parameters, ParseContext context) {
		return parse(value.asMulti(), dataType, parameters, context);
	}

	private RecurrenceDates parse(List<String> valueStrs, ICalDataType dataType, ICalParameters parameters, ParseContext context) {
		String tzid = parameters.getTimezoneId();
		RecurrenceDates property = new RecurrenceDates();

		if (dataType == PERIOD) {
			//parse as periods
			for (String timePeriodStr : valueStrs) {
				String timePeriodStrSplit[] = timePeriodStr.split("/");

				if (timePeriodStrSplit.length < 2) {
					context.addWarning(13);
					continue;
				}

				String startStr = timePeriodStrSplit[0];
				Date start;
				try {
					start = ICalDateFormat.parse(startStr);
				} catch (IllegalArgumentException e) {
					context.addWarning(10, startStr);
					continue;
				}

				String endStr = timePeriodStrSplit[1];
				Date end = null;
				try {
					end = ICalDateFormat.parse(endStr);
					property.addPeriod(new Period(start, end));
				} catch (IllegalArgumentException e) {
					//must be a duration
					try {
						Duration duration = Duration.parse(endStr);
						property.addPeriod(new Period(start, duration));
					} catch (IllegalArgumentException e2) {
						context.addWarning(14, endStr);
						continue;
					}
				}

				if (!ICalDateFormat.isUTC(startStr)) {
					if (tzid == null) {
						context.addFloatingDate(property, start, startStr);
					} else {
						context.addTimezonedDate(tzid, property, start, startStr);
					}
				}

				if (end != null && !ICalDateFormat.isUTC(endStr)) {
					if (tzid == null) {
						context.addFloatingDate(property, end, endStr);
					} else {
						context.addTimezonedDate(tzid, property, end, endStr);
					}
				}
			}
			return property;
		}

		//parse as dates
		boolean hasTime = (dataType == DATE_TIME);
		for (String s : valueStrs) {
			Date date;
			try {
				date = ICalDateFormat.parse(s);
			} catch (IllegalArgumentException e) {
				context.addWarning(15, s);
				continue;
			}
			DateTimeComponents components = DateTimeComponents.parse(s);
			ICalDate icalDate = new ICalDate(date, components, hasTime);
			property.addDate(icalDate);

			if (hasTime && !ICalDateFormat.isUTC(s)) {
				if (tzid == null) {
					context.addFloatingDate(property, icalDate, s);
				} else {
					context.addTimezonedDate(tzid, property, icalDate, s);
				}
			}
		}
		return property;
	}
}
